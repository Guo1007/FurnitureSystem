package gcy.system.service.Impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.*;
import gcy.system.entity.vo.CommentAppendVO;
import gcy.system.entity.vo.CommentVO;
import gcy.system.exception.BusinessException;
import gcy.system.listener.AiReviewConsumer.AiReviewMessage;
import gcy.system.mapper.*;
import gcy.system.service.ICommentService;
import gcy.system.utils.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评价服务实现类，负责商品评价的查询、发表、追评、删除等核心业务逻辑。
 * 包括评价的分页查询、追评管理、订单状态联动以及评价与追评的软删除处理。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements ICommentService {

    private final GoodsCommentMapper goodsCommentMapper;

    private final CommentAppendMapper commentAppendMapper;

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final ReviewCommentMapper reviewCommentMapper;

    private final NotificationMapper notificationMapper;

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 根据商品ID分页查询该商品下的所有评价列表，包含当前用户的点赞状态和追评信息。
     * 通过分页参数控制返回的数据量，支持不传分页参数时使用默认值。
     *
     * @param goodsId 商品ID，用于筛选属于该商品的评价
     * @param userId  当前登录用户ID，用于判断评论是否已被当前用户点赞
     * @param current 当前页码，为null时默认第1页
     * @param size    每页条数，为null时默认10条
     * @return 包含分页评价列表及追评信息的统一返回结果
     */
    @Override
    public Result getCommentsByGoodsId(Long goodsId, Long userId, Integer current, Integer size) {
        Page<CommentVO> page = new Page<>(current != null ? current : 1, size != null ? size : 10);
        Page<CommentVO> result = goodsCommentMapper.selectCommentsByGoodsId(goodsId, userId, page);
        fillAppendList(result.getRecords(), userId);
        return Result.ok(result);
    }

    /**
     * 根据订单ID查询该订单下所有商品的评价列表，包含当前用户的点赞状态和追评信息。
     * 通常用于用户查看自己某个订单的完整评价情况。
     *
     * @param orderId 订单ID，用于筛选属于该订单的评价
     * @param userId  当前登录用户ID，用于判断评论是否已被当前用户点赞
     * @return 包含该订单下所有评价及追评信息的统一返回结果
     */
    @Override
    public Result getCommentsByOrderId(Long orderId, Long userId) {
        // 越权防护：仅订单所属用户可查看该订单下的评价
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该订单的评价");
        }
        List<CommentVO> comments = goodsCommentMapper.selectCommentsByOrderId(orderId, userId);
        fillAppendList(comments, userId);
        return Result.ok(comments);
    }

    /**
     * 为评价列表批量填充每条评价对应的追评列表。
     * 通过一次批量查询所有相关追评并按主评论ID分组后，再分别设置到各条评价中，
     * 避免了逐条查询追评的N+1问题。
     *
     * @param comments 需要填充追评的评价列表
     * @param userId   当前登录用户ID，用于关联追评的点赞状态
     */
    private void fillAppendList(List<CommentVO> comments, Long userId) {
        if (comments.isEmpty()) return;
        List<Long> commentIds = comments.stream().map(CommentVO::getId).collect(Collectors.toList());
        List<CommentAppendVO> allAppends = commentAppendMapper.selectByMainCommentIds(commentIds, userId);
        Map<Long, List<CommentAppendVO>> appendMap = allAppends.stream()
                .collect(Collectors.groupingBy(CommentAppendVO::getMainCommentId));
        for (CommentVO comment : comments) {
            comment.setAppendList(appendMap.getOrDefault(comment.getId(), Collections.emptyList()));
        }
    }

    /**
     * 发表一条新的商品评价。执行前会进行多重校验：验证订单是否存在且属于当前用户、
     * 订单状态是否允许评价（已完成或已评价状态）、评价的商品是否属于该订单、
     * 以及用户是否已经评价过该商品。校验通过后插入评价记录，并根据该订单下是否所有商品
     * 都已完成评价来更新订单状态为"已评价"或"已完成"。
     *
     * @param comment 评价实体，包含订单ID、商品ID、评价内容、评分等信息
     * @param userId  当前登录用户ID，作为评价的发表者
     * @return 操作成功的统一返回结果
     * @throws BusinessException 当订单不存在、无权评价、订单状态不允许评价、
     *                           商品不在订单中、或已评价过该商品时抛出业务异常
     */
    @Override
    @Transactional
    public Result addComment(GoodsComment comment, Long userId) {
        // 校验订单归属：只能评价自己的订单
        Order order = orderMapper.selectById(comment.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权评价该订单");
        }
        // 已完成/已评价的订单才允许评价
        if (order.getStatus() != OrderStatus.COMPLETED.getCode()
                && order.getStatus() != OrderStatus.REVIEWED.getCode()) {
            throw new BusinessException("订单状态不允许评价");
        }
        // 校验 goodsId 属于该订单
        if (orderItemMapper.selectCount(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, comment.getOrderId())
                        .eq(OrderItem::getFurnitureId, comment.getGoodsId())) == 0) {
            throw new BusinessException("该商品不在该订单中");
        }
        GoodsComment existing = goodsCommentMapper.selectByOrderAndGoods(
                comment.getOrderId(), userId, comment.getGoodsId());
        if (existing != null) {
            throw new BusinessException("您已评价过该商品");
        }
        comment.setUserId(userId);
        comment.setStatus(0);
        comment.setHasAppend(0);
        comment.setCreateTime(LocalDateTime.now());
        try {
            goodsCommentMapper.insert(comment);
        } catch (DuplicateKeyException e) {
            // 软删除后再次评价会被 uk_order_user_goods 唯一索引拦截
            throw new BusinessException("您已评价过该商品");
        }
        // 发送AI自动审核消息（异步，不阻塞用户请求）
        sendAiReviewMessage("goods_comment", comment.getId());
        log.info("发表评价: commentId={}, orderId={}, goodsId={}, userId={}",
                comment.getId(), comment.getOrderId(), comment.getGoodsId(), userId);
        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, comment.getOrderId()));
        // 必须与 recalculateOrderStatus 口径一致：只统计未软删的评价。
        // 此前这里漏了 user_deleted 过滤，会把用户已删除的评价也算作「已评价」，
        // 导致订单被错误置为 REVIEWED，用户再也评不了该商品。
        List<GoodsComment> existingComments = goodsCommentMapper.selectList(
                new LambdaQueryWrapper<GoodsComment>()
                        .eq(GoodsComment::getOrderId, comment.getOrderId())
                        .eq(GoodsComment::getUserId, userId)
                        .eq(GoodsComment::getUserDeleted, 0));
        Set<Long> reviewedGoodsIds = existingComments.stream()
                .map(GoodsComment::getGoodsId).collect(Collectors.toSet());
        boolean allReviewed = !orderItems.isEmpty() && orderItems.stream()
                .allMatch(item -> reviewedGoodsIds.contains(item.getFurnitureId()));
        // CAS：只允许从「已完成(3)」或「已评价(5)」推进评价状态。
        // 无条件覆盖的话，并发下可能把用户刚提交的「申请退款中(6)」静默打回「已完成」，
        // 用户的退款申请会凭空消失。
        int targetStatus = allReviewed
                ? OrderStatus.REVIEWED.getCode()
                : OrderStatus.COMPLETED.getCode();
        orderMapper.update(null,
                new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, comment.getOrderId())
                        .in(Order::getStatus,
                                OrderStatus.COMPLETED.getCode(),
                                OrderStatus.REVIEWED.getCode())
                        .set(Order::getStatus, targetStatus));
        return Result.ok();
    }

    /**
     * 对已有评价进行追评。先验证主评价是否存在且属于当前用户，
     * 然后根据已有追评数量自动生成追评序号，插入追评记录后更新主评价的追评标记和最新追评时间。
     *
     * @param append 追评实体，包含主评论ID、追评内容等信息
     * @param userId 当前登录用户ID，作为追评的发表者
     * @return 操作成功的统一返回结果
     * @throws BusinessException 当主评价不存在、主评价不属于当前用户、
     *                           或并发追评导致序号冲突时抛出业务异常
     */
    @Override
    @Transactional
    public Result appendComment(CommentAppend append, Long userId) {
        GoodsComment mainComment = goodsCommentMapper.selectById(append.getMainCommentId());
        if (mainComment == null) {
            throw new BusinessException("评价不存在");
        }
        if (!mainComment.getUserId().equals(userId)) {
            throw new BusinessException("只能追评自己的评价");
        }
        int appendCount = commentAppendMapper.countByMainCommentId(append.getMainCommentId());
        append.setUserId(userId);
        append.setAppendNum(appendCount + 1);
        append.setStatus(0);
        append.setAppendTime(LocalDateTime.now());
        try {
            commentAppendMapper.insert(append);
        } catch (DuplicateKeyException e) {
            // 并发追评导致 appendNum 冲突（极低概率），提示重试
            log.debug("追评序号冲突: mainCommentId={}", append.getMainCommentId());
            throw new BusinessException("操作繁忙，请稍后重试");
        }
        // 发送AI自动审核消息（异步，不阻塞用户请求）
        sendAiReviewMessage("comment_append", append.getId());
        goodsCommentMapper.update(null,
                new LambdaUpdateWrapper<GoodsComment>()
                        .eq(GoodsComment::getId, append.getMainCommentId())
                        .set(GoodsComment::getHasAppend, 1)
                        .set(GoodsComment::getLatestAppendTime, LocalDateTime.now()));
        return Result.ok();
    }

    /**
     * 软删除一条评价。将评价的 user_deleted 标记置为1，实现逻辑删除。
     * 仅评价的发表者本人可以执行删除操作。
     *
     * @param commentId 要删除的评价ID
     * @param userId    当前登录用户ID，用于校验是否为评价的发表者
     * @return 操作成功的统一返回结果
     * @throws BusinessException 当评价不存在或当前用户无权删除时抛出业务异常
     */
    @Override
    @Transactional
    public Result deleteComment(Long commentId, Long userId) {
        GoodsComment comment = goodsCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评价不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的评价");
        }
        doDeleteReview(comment);
        return Result.ok();
    }

    /**
     * 软删除一条追评。将追评的 user_deleted 标记置为1，实现逻辑删除。
     * 仅追评的发表者本人可以执行删除操作。
     *
     * @param appendId 要删除的追评ID
     * @param userId   当前登录用户ID，用于校验是否为追评的发表者
     * @return 操作成功的统一返回结果
     * @throws BusinessException 当追评不存在或当前用户无权删除时抛出业务异常
     */
    @Override
    @Transactional
    public Result deleteAppend(Long appendId, Long userId) {
        CommentAppend append = commentAppendMapper.selectById(appendId);
        if (append == null) {
            throw new BusinessException("追评不存在");
        }
        if (!append.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的追评");
        }
        commentAppendMapper.update(null,
                new LambdaUpdateWrapper<CommentAppend>()
                        .eq(CommentAppend::getId, appendId)
                        .set(CommentAppend::getUserDeleted, 1));
        return Result.ok();
    }

    /**
     * 软删除一条评价及其所有关联数据。会级联软删除该评价下的所有追评、
     * 所有回复（回复追评的记录），以及清理通知表中对该评价的引用，
     * 确保数据一致性。仅评价的发表者本人可以执行此操作。
     *
     * @param reviewId 要删除的评价ID
     * @param userId   当前登录用户ID，用于校验是否为评价的发表者
     * @return 操作成功的统一返回结果
     * @throws BusinessException 当评价不存在或当前用户无权删除时抛出业务异常
     */
    @Override
    @Transactional
    public Result deleteReview(Long reviewId, Long userId) {
        GoodsComment review = goodsCommentMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的评价");
        }
        doDeleteReview(review);
        return Result.ok();
    }

    /**
     * 级联软删除一条评价及其关联数据，并重算订单评价状态。
     * <p>
     * 统一删除路径（单条删除 / 删除整条评价均走此逻辑）：级联软删除追评、回复，
     * 清理通知引用，并按剩余有效评价重算订单状态：
     * 若订单全部商品仍有有效评价则保持"已评价"，否则回退为"已完成"以允许重新评价。
     * </p>
     *
     * @param review 待删除的评价实体
     */
    private void doDeleteReview(GoodsComment review) {
        Long reviewId = review.getId();
        // 主评价与关联数据一并物理删除。
        //
        // 为什么主评价不能用软删：goods_comment 上有唯一索引 uk_order_user_goods
        // (order_id, user_id, goods_id)，软删只是把 user_deleted 置 1，记录仍然占着唯一键，
        // 用户删除后想重新评价时 insert 必然撞索引 → 100% 失败，
        // 而 recalculateOrderStatus 承诺的「删除后可重新评价」根本兑现不了。
        // 追评/回复不参与该唯一键，本可软删，但主评价已物理删除，
        // 留下它们会变成无主孤儿数据，故一并清理。
        commentAppendMapper.delete(new LambdaQueryWrapper<CommentAppend>()
                .eq(CommentAppend::getMainCommentId, reviewId));
        reviewCommentMapper.delete(new LambdaQueryWrapper<ReviewComment>()
                .eq(ReviewComment::getReviewId, reviewId));
        goodsCommentMapper.deleteById(reviewId);
        // 清理通知中的评论引用
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewId, null)
                        .eq(Notification::getReviewId, reviewId));
        // 重算订单评价状态
        recalculateOrderStatus(review.getOrderId(), review.getUserId());
    }

    /**
     * 按订单剩余的有效评价重算订单状态。
     * <p>
     * 若订单所有商品均有有效评价（未软删）则订单置为"已评价"，否则置回"已完成"，
     * 使已删除全部评价的订单可重新评价。
     * </p>
     *
     * @param orderId 订单ID
     * @param userId  评价所属用户ID
     */
    private void recalculateOrderStatus(Long orderId, Long userId) {
        if (orderId == null || userId == null) return;
        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId));
        if (orderItems.isEmpty()) return;
        List<GoodsComment> remainComments = goodsCommentMapper.selectList(
                new LambdaQueryWrapper<GoodsComment>()
                        .eq(GoodsComment::getOrderId, orderId)
                        .eq(GoodsComment::getUserId, userId)
                        .eq(GoodsComment::getUserDeleted, 0));
        Set<Long> reviewedGoodsIds = remainComments.stream()
                .map(GoodsComment::getGoodsId).collect(Collectors.toSet());
        boolean allReviewed = orderItems.stream()
                .allMatch(item -> reviewedGoodsIds.contains(item.getFurnitureId()));
        int targetStatus = allReviewed ? OrderStatus.REVIEWED.getCode() : OrderStatus.COMPLETED.getCode();
        orderMapper.update(null,
                new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .set(Order::getStatus, targetStatus));
    }

    /**
     * 发送AI自动审核消息到MQ。
     * <p>
     * 发送失败仅记录日志，不阻塞主流程（审核为异步增强，非关键路径）。
     * </p>
     *
     * @param type 审核类型：goods_comment / comment_append / review_comment
     * @param id   对应记录的ID
     */
    private void sendAiReviewMessage(String type, Long id) {
        try {
            AiReviewMessage msg = new AiReviewMessage(type, id);
            String json = JSONUtil.toJsonStr(msg);
            // 事务未提交就发 MQ，消费者可能查不到这条记录；一旦回滚，消息更是不可能撤回。
            // 统一改为「提交后再发」，与 CommentManageServiceImpl 里的 afterCommit 范式保持一致。
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        doSendAiReviewMessage(json, type, id);
                    }
                });
            } else {
                doSendAiReviewMessage(json, type, id);
            }
        } catch (Exception e) {
            log.error("发送AI审核消息失败: type={}, id={}", type, id, e);
        }
    }

    private void doSendAiReviewMessage(String json, String type, Long id) {
        try {
            rocketMQTemplate.convertAndSend("comment-auto-review-topic", json);
            log.debug("AI审核消息已发送: type={}, id={}", type, id);
        } catch (Exception e) {
            log.error("发送AI审核消息失败: type={}, id={}", type, id, e);
        }
    }
}
