package gcy.system.service.Impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import gcy.system.utils.AfterCommit;
import gcy.system.utils.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评价服务实现类。
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
     * 分页查询商品的评价列表，含当前用户点赞状态与追评。
     *
     * @param current 为 null 时默认第 1 页
     * @param size    为 null 时默认 10 条
     */
    @Override
    public Result getCommentsByGoodsId(Long goodsId, Long userId, Integer current, Integer size) {
        Page<CommentVO> page = new Page<>(current != null ? current : 1, size != null ? size : 10);
        Page<CommentVO> result = goodsCommentMapper.selectCommentsByGoodsId(goodsId, userId, page);
        fillAppendList(result.getRecords(), userId);
        fillCommentCounts(result.getRecords());
        return Result.ok(result);
    }

    /**
     * 查询订单下的评价列表（仅订单所属用户），含点赞状态与追评。
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
        fillCommentCounts(comments);
        return Result.ok(comments);
    }

    /**
     * 批量填充评价的追评列表：一次查询按主评论ID分组，避免 N+1。
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
     * 批量填充每条评价的评论数（列表页角标）：一次 {@code GROUP BY review_id} 取回本页计数。
     * <p>
     * 口径取 {@code user_deleted = 0}，与展开评论区后实际渲染条数一致；不复用
     * {@code selectByReviewId} 的「自己删的对自己可见」分支，否则角标会比展开多几条。
     * 回复已扁平化到根节点，行数 == 渲染条数；若库中存在孙节点脏数据，此 COUNT 会略大于前端递归统计。
     */
    private void fillCommentCounts(List<CommentVO> comments) {
        if (comments == null || comments.isEmpty()) return;
        List<Long> reviewIds = comments.stream()
                .map(CommentVO::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (reviewIds.isEmpty()) return;
        Map<Long, Integer> countMap = new HashMap<>();
        try {
            QueryWrapper<ReviewComment> qw = new QueryWrapper<ReviewComment>()
                    .select("review_id", "COUNT(*) AS cnt")
                    .in("review_id", reviewIds)
                    .eq("deleted", 0)
                    .eq("user_deleted", 0)
                    .eq("status", 1)
                    .groupBy("review_id");
            for (Map<String, Object> row : reviewCommentMapper.selectMaps(qw)) {
                Object rid = row.get("review_id");
                Object cnt = row.get("cnt");
                if (rid instanceof Number && cnt instanceof Number) {
                    countMap.put(((Number) rid).longValue(), ((Number) cnt).intValue());
                }
            }
        } catch (Exception e) {
            // 计数失败不该让整个评价列表接口挂掉，退化为 0（前端角标不显示）
            log.warn("批量统计评价评论数失败，角标将显示为0: {}", e.getMessage());
        }
        for (CommentVO comment : comments) {
            comment.setCommentCount(countMap.getOrDefault(comment.getId(), 0));
        }
    }

    /**
     * 发表评价：校验订单归属、状态（已完成/已评价）、商品属于该订单且未重复评价，
     * 插入后按订单是否全部商品已评价更新订单状态。
     */
    @Override
    @Transactional
    public Result addComment(GoodsComment comment, Long userId) {
        Order order = orderMapper.selectById(comment.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权评价该订单");
        }
        if (order.getStatus() != OrderStatus.COMPLETED.getCode()
                && order.getStatus() != OrderStatus.REVIEWED.getCode()) {
            throw new BusinessException("订单状态不允许评价");
        }
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
        // 必须与 recalculateOrderStatus 口径一致：只统计未软删的评价；
        // 漏掉 user_deleted 过滤会把已删除评价也算作「已评价」，订单被错误置为 REVIEWED。
        List<GoodsComment> existingComments = goodsCommentMapper.selectList(
                new LambdaQueryWrapper<GoodsComment>()
                        .eq(GoodsComment::getOrderId, comment.getOrderId())
                        .eq(GoodsComment::getUserId, userId)
                        .eq(GoodsComment::getUserDeleted, 0));
        Set<Long> reviewedGoodsIds = existingComments.stream()
                .map(GoodsComment::getGoodsId).collect(Collectors.toSet());
        boolean allReviewed = !orderItems.isEmpty() && orderItems.stream()
                .allMatch(item -> reviewedGoodsIds.contains(item.getFurnitureId()));
        // CAS：只允许从「已完成(3)」或「已评价(5)」推进，避免并发下把用户刚提交的
        // 「申请退款中(6)」静默打回「已完成」。
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
     * 追评：校验主评价归属，按已有追评数生成序号并插入，同步主评价的追评标记与最新追评时间。
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
     * 软删除评价（user_deleted=1），仅发表者本人可删。
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
     * 软删除追评（user_deleted=1），仅发表者本人可删。
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
     * 删除评价并级联清理其追评、回复及通知引用，随后重算订单评价状态；仅发表者本人可删。
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
     * 统一删除路径（deleteComment / deleteReview 均走此逻辑）：级联删除追评、回复，
     * 清理通知引用，并按剩余有效评价重算订单状态——全商品仍有有效评价则保持「已评价」，
     * 否则回退「已完成」以允许重新评价。
     */
    private void doDeleteReview(GoodsComment review) {
        Long reviewId = review.getId();
        // 主评价必须物理删除：uk_order_user_goods(order_id, user_id, goods_id) 下软删仍占唯一键，
        // 用户删后重新评价 insert 必撞索引；追评/回复可软删，但主评价已物理删除，
        // 留下会成孤儿，故一并清理。
        commentAppendMapper.delete(new LambdaQueryWrapper<CommentAppend>()
                .eq(CommentAppend::getMainCommentId, reviewId));
        reviewCommentMapper.delete(new LambdaQueryWrapper<ReviewComment>()
                .eq(ReviewComment::getReviewId, reviewId));
        goodsCommentMapper.deleteById(reviewId);
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewId, null)
                        .eq(Notification::getReviewId, reviewId));
        recalculateOrderStatus(review.getOrderId(), review.getUserId());
    }

    /**
     * 按订单剩余有效评价（未软删）重算状态：全商品均有有效评价则「已评价」，
     * 否则回退「已完成」以允许重新评价。
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
     * 发送 AI 审核消息到 MQ（type: goods_comment / comment_append / review_comment）；
     * 失败仅记日志，不阻塞主流程（审核为异步增强）。
     */
    private void sendAiReviewMessage(String type, Long id) {
        try {
            AiReviewMessage msg = new AiReviewMessage(type, id);
            String json = JSONUtil.toJsonStr(msg);
            // 事务未提交就发 MQ，消费者可能查不到这条记录；一旦回滚，消息更是不可能撤回。
            // 统一走 AfterCommit（提交后发送，无事务时立即发送）。
            AfterCommit.run(() -> doSendAiReviewMessage(json, type, id));
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
