package gcy.system.service.admin.Impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.*;
import gcy.system.entity.vo.admin.AdminAppendVO;
import gcy.system.entity.vo.admin.AdminCommentVO;
import gcy.system.entity.vo.admin.AdminReviewCommentVO;
import gcy.system.exception.BusinessException;
import gcy.system.listener.CommentReplyListener;
import gcy.system.mapper.*;
import gcy.system.mapper.admin.CommentManageMapper;
import gcy.system.service.admin.ICommentManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论管理服务实现类，提供商品评价、追评以及评价评论的审核、删除和查询等管理功能。
 * 支持分页查询、单条及批量操作，并在审核评价评论时通过RocketMQ发送回复通知。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentManageServiceImpl implements ICommentManageService {

    private final GoodsCommentMapper goodsCommentMapper;

    private final CommentAppendMapper commentAppendMapper;

    private final ReviewCommentMapper reviewCommentMapper;

    private final CommentManageMapper commentManageMapper;

    private final RocketMQTemplate rocketMQTemplate;

    private final UserMapper userMapper;

    private final NotificationMapper notificationMapper;

    /**
     * 分页获取所有的商品评价（含用户信息和商品信息），用于管理员统一查看评价列表。
     *
     * @param current 当前页码
     * @param size    每页条数
     * @return 包含分页评价数据的 {@link Result} 对象
     */
    @Override
    public Result getAllComments(Integer current, Integer size, String statuses) {
        Page<AdminCommentVO> page = new Page<>(current, size);
        Page<AdminCommentVO> result = commentManageMapper.selectAllComments(page, parseStatusList(statuses));
        return Result.ok(result);
    }

    /**
     * 审核通过指定的商品评价。将评价状态更新为已通过（状态码1）。
     *
     * @param commentId 待审核通过的评价ID
     * @return 操作成功的 {@link Result} 对象
     * @throws BusinessException 如果评价不存在
     */
    @Override
    @Transactional
    public Result approveComment(Long commentId) {
        GoodsComment comment = goodsCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评价不存在");
        }
        goodsCommentMapper.update(null,
                new LambdaUpdateWrapper<GoodsComment>()
                        .eq(GoodsComment::getId, commentId)
                        .set(GoodsComment::getStatus, 1));
        return Result.ok();
    }

    /**
     * 审核拒绝指定的商品评价。将评价状态更新为已拒绝（状态码2）。
     *
     * @param commentId 待审核拒绝的评价ID
     * @return 操作成功的 {@link Result} 对象
     * @throws BusinessException 如果评价不存在
     */
    @Override
    @Transactional
    public Result rejectComment(Long commentId, String rejectReason) {
        GoodsComment comment = goodsCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评价不存在");
        }
        if (rejectReason == null || rejectReason.trim().isEmpty()) {
            throw new BusinessException("拒绝原因不能为空");
        }
        goodsCommentMapper.update(null,
                new LambdaUpdateWrapper<GoodsComment>()
                        .eq(GoodsComment::getId, commentId)
                        .set(GoodsComment::getStatus, 2)
                        .set(GoodsComment::getManualRejectReason, rejectReason));
        // 发送审核拒绝通知
        String commentContent = comment.getContent();
        if (commentContent != null && commentContent.length() > 100) {
            commentContent = commentContent.substring(0, 100) + "...";
        }
        String notifyContent = "评论内容：" + (commentContent != null ? commentContent : "无") +
                "\n审核结果：未通过" +
                "\n拒绝原因：" + rejectReason;
        Notification notification = new Notification();
        notification.setUserId(comment.getUserId());
        notification.setTitle("您的评价审核未通过");
        notification.setContent(notifyContent);
        notification.setType("comment_reject");
        notification.setReviewId(commentId);
        notification.setGoodsId(comment.getGoodsId());
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);
        return Result.ok();
    }

    /**
     * 分页获取所有的追评数据，用于管理员统一查看追评列表。
     *
     * @param current 当前页码
     * @param size    每页条数
     * @return 包含分页追评数据的 {@link Result} 对象
     */
    @Override
    public Result getAllAppends(Integer current, Integer size, String statuses) {
        Page<AdminAppendVO> page = new Page<>(current, size);
        Page<AdminAppendVO> result = commentManageMapper.selectAllAppends(page, parseStatusList(statuses));
        return Result.ok(result);
    }

    /**
     * 审核通过指定的追评。将追评状态更新为已通过（状态码1）。
     *
     * @param appendId 待审核通过的追评ID
     * @return 操作成功的 {@link Result} 对象
     * @throws BusinessException 如果追评不存在
     */
    @Override
    @Transactional
    public Result approveAppend(Long appendId) {
        CommentAppend append = commentAppendMapper.selectById(appendId);
        if (append == null) {
            throw new BusinessException("追评不存在");
        }
        commentAppendMapper.update(null,
                new LambdaUpdateWrapper<CommentAppend>()
                        .eq(CommentAppend::getId, appendId)
                        .set(CommentAppend::getStatus, 1));
        return Result.ok();
    }

    /**
     * 审核拒绝指定的追评。将追评状态更新为已拒绝（状态码2）。
     *
     * @param appendId 待审核拒绝的追评ID
     * @return 操作成功的 {@link Result} 对象
     * @throws BusinessException 如果追评不存在
     */
    @Override
    @Transactional
    public Result rejectAppend(Long appendId, String rejectReason) {
        CommentAppend append = commentAppendMapper.selectById(appendId);
        if (append == null) {
            throw new BusinessException("追评不存在");
        }
        if (rejectReason == null || rejectReason.trim().isEmpty()) {
            throw new BusinessException("拒绝原因不能为空");
        }
        commentAppendMapper.update(null,
                new LambdaUpdateWrapper<CommentAppend>()
                        .eq(CommentAppend::getId, appendId)
                        .set(CommentAppend::getStatus, 2)
                        .set(CommentAppend::getManualRejectReason, rejectReason));
        // 发送审核拒绝通知
        GoodsComment mainComment = goodsCommentMapper.selectById(append.getMainCommentId());
        String appendContent = append.getAppendContent();
        if (appendContent != null && appendContent.length() > 100) {
            appendContent = appendContent.substring(0, 100) + "...";
        }
        String notifyContent = "追评内容：" + (appendContent != null ? appendContent : "无") +
                "\n审核结果：未通过" +
                "\n拒绝原因：" + rejectReason;
        Notification notification = new Notification();
        notification.setUserId(append.getUserId());
        notification.setTitle("您的追评审核未通过");
        notification.setContent(notifyContent);
        notification.setType("append_reject");
        notification.setReviewId(append.getMainCommentId());
        notification.setGoodsId(mainComment != null ? mainComment.getGoodsId() : null);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);
        return Result.ok();
    }

    /**
     * 分页获取所有的评价评论（回复）数据，用于管理员统一查看评论回复列表。
     *
     * @param current 当前页码
     * @param size    每页条数
     * @return 包含分页评价评论数据的 {@link Result} 对象
     */
    @Override
    public Result getAllReviewComments(Integer current, Integer size, String statuses) {
        Page<AdminReviewCommentVO> page = new Page<>(current, size);
        Page<AdminReviewCommentVO> result = commentManageMapper.selectAllReviewComments(page, parseStatusList(statuses));
        return Result.ok(result);
    }

    /**
     * 审核通过指定的评价评论（回复）。将评论状态更新为已通过（状态码1），
     * 如果该评论是针对其他用户的回复（非自回复），则通过RocketMQ发送评论回复通知。
     *
     * @param commentId 待审核通过的评价评论ID
     * @return 操作成功的 {@link Result} 对象
     * @throws BusinessException 如果评论不存在
     */
    @Override
    @Transactional
    public Result approveReviewComment(Long commentId) {
        ReviewComment comment = reviewCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        reviewCommentMapper.update(null,
                new LambdaUpdateWrapper<ReviewComment>()
                        .eq(ReviewComment::getId, commentId)
                        .set(ReviewComment::getStatus, 1));
        // 事务提交后再发送 MQ 通知，避免事务回滚时通知已发出（通知与审核状态不一致）
        if (comment.getReplyToUserId() != null && !comment.getReplyToUserId().equals(comment.getUserId())) {
            User replyUser = userMapper.selectById(comment.getUserId());
            String userName = replyUser != null ? replyUser.getUserName() : "用户";
            GoodsComment goodsComment = goodsCommentMapper.selectById(comment.getReviewId());
            Long goodsId = goodsComment != null ? goodsComment.getGoodsId() : null;
            CommentReplyListener.CommentReplyMessage msg = new CommentReplyListener.CommentReplyMessage(
                    comment.getReplyToUserId(),
                    comment.getReviewId(),
                    goodsId,
                    comment.getId(),
                    comment.getUserId(),
                    userName,
                    userName + " 回复了你的评论"
            );
            String json = JSONUtil.toJsonStr(msg);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendReplyNotification(json);
                    }
                });
            } else {
                sendReplyNotification(json);
            }
        }
        return Result.ok();
    }

    /**
     * 发送评论回复通知消息到 MQ。
     * <p>
     * 发送失败仅记录日志，不影响审核主流程（通知为异步增强，非关键路径）。
     * </p>
     *
     * @param json 序列化后的通知消息体
     */
    private void sendReplyNotification(String json) {
        try {
            rocketMQTemplate.convertAndSend("comment-reply-topic", json);
            log.info("评论回复通知已发送: {}", json);
        } catch (Exception e) {
            log.error("发送评论回复通知失败", e);
        }
    }

    /**
     * 审核拒绝指定的评价评论（回复）。将评论状态更新为已拒绝（状态码2）。
     *
     * @param commentId 待审核拒绝的评价评论ID
     * @return 操作成功的 {@link Result} 对象
     * @throws BusinessException 如果评论不存在
     */
    @Override
    @Transactional
    public Result rejectReviewComment(Long commentId, String rejectReason) {
        ReviewComment comment = reviewCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        if (rejectReason == null || rejectReason.trim().isEmpty()) {
            throw new BusinessException("拒绝原因不能为空");
        }
        reviewCommentMapper.update(null,
                new LambdaUpdateWrapper<ReviewComment>()
                        .eq(ReviewComment::getId, commentId)
                        .set(ReviewComment::getStatus, 2)
                        .set(ReviewComment::getManualRejectReason, rejectReason));
        // 发送审核拒绝通知
        GoodsComment goodsComment = goodsCommentMapper.selectById(comment.getReviewId());
        String replyContent = comment.getContent();
        if (replyContent != null && replyContent.length() > 100) {
            replyContent = replyContent.substring(0, 100) + "...";
        }
        String notifyContent = "回复内容：" + (replyContent != null ? replyContent : "无") +
                "\n审核结果：未通过" +
                "\n拒绝原因：" + rejectReason;
        Notification notification = new Notification();
        notification.setUserId(comment.getUserId());
        notification.setTitle("您的回复审核未通过");
        notification.setContent(notifyContent);
        notification.setType("reply_reject");
        notification.setReviewId(comment.getReviewId());
        notification.setReviewCommentId(commentId);
        notification.setGoodsId(goodsComment != null ? goodsComment.getGoodsId() : null);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);
        return Result.ok();
    }

    /**
     * 获取待审核的商品评价、追评和评价评论的总数量，用于管理员仪表盘展示待处理事项。
     *
     * @return 包含三种待审核数量的 {@link Result} 对象，Map中的key分别为commentCount、appendCount、reviewCommentCount
     */
    @Override
    public Result getPendingCount() {
        long commentCount = goodsCommentMapper.selectCount(
                new LambdaQueryWrapper<GoodsComment>().in(GoodsComment::getStatus, 0, 3));
        long appendCount = commentAppendMapper.selectCount(
                new LambdaQueryWrapper<CommentAppend>().in(CommentAppend::getStatus, 0, 3));
        long reviewCommentCount = reviewCommentMapper.selectCount(
                new LambdaQueryWrapper<ReviewComment>().in(ReviewComment::getStatus, 0, 3));
        return Result.ok(java.util.Map.of(
                "commentCount", commentCount,
                "appendCount", appendCount,
                "reviewCommentCount", reviewCommentCount));
    }

    /**
     * 获取各状态数量统计，用于管理端 Tab 计数。
     * <p>
     * 返回三种类型（评价、追评、回复）各四个状态（全部、待审核、已通过、已拒绝）的数量。
     * </p>
     *
     * @return 包含各状态数量的统一返回结果
     */
    @Override
    public Result getStatusCounts() {
        // 原实现是 12 次串行 COUNT(*)，其中 selectCount(null) 连索引都用不上。
        // 改为每种表一条 GROUP BY status，3 次查询取回全部 12 个数字。
        Map<Integer, Long> c = countByStatus(goodsCommentMapper);
        long commentAll = sum(c);
        long commentPending = c.getOrDefault(0, 0L) + c.getOrDefault(3, 0L);
        long commentApproved = c.getOrDefault(1, 0L);
        long commentRejected = c.getOrDefault(2, 0L);

        Map<Integer, Long> a = countByStatus(commentAppendMapper);
        long appendAll = sum(a);
        long appendPending = a.getOrDefault(0, 0L) + a.getOrDefault(3, 0L);
        long appendApproved = a.getOrDefault(1, 0L);
        long appendRejected = a.getOrDefault(2, 0L);

        Map<Integer, Long> r = countByStatus(reviewCommentMapper);
        long replyAll = sum(r);
        long replyPending = r.getOrDefault(0, 0L) + r.getOrDefault(3, 0L);
        long replyApproved = r.getOrDefault(1, 0L);
        long replyRejected = r.getOrDefault(2, 0L);

        return Result.ok(java.util.Map.of(
                "comment", java.util.Map.of(
                        "all", commentAll, "pending", commentPending,
                        "approved", commentApproved, "rejected", commentRejected),
                "append", java.util.Map.of(
                        "all", appendAll, "pending", appendPending,
                        "approved", appendApproved, "rejected", appendRejected),
                "reviewComment", java.util.Map.of(
                        "all", replyAll, "pending", replyPending,
                        "approved", replyApproved, "rejected", replyRejected)));
    }

    /**
     * 按 status 分组计数：一条 SQL 取回该表各状态的数量（status -> count）。
     * 逻辑删除过滤由 MyBatis-Plus 自动追加，与原 selectCount 语义一致。
     */
    private <T> Map<Integer, Long> countByStatus(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper) {
        QueryWrapper<T> qw = new QueryWrapper<T>()
                .select("status AS s", "COUNT(*) AS c")
                .groupBy("status");
        Map<Integer, Long> map = new HashMap<>();
        for (Map<String, Object> row : mapper.selectMaps(qw)) {
            Object s = row.get("s");
            Object cnt = row.get("c");
            if (s instanceof Number && cnt instanceof Number) {
                map.put(((Number) s).intValue(), ((Number) cnt).longValue());
            }
        }
        return map;
    }

    private long sum(Map<Integer, Long> statusCount) {
        long total = 0L;
        for (Long v : statusCount.values()) {
            total += v == null ? 0L : v;
        }
        return total;
    }

    /**
     * 删除指定的商品评价。先级联软删除关联的追评和评价评论（将deleted标记设为1），
     * 再删除评价本身，最后清理相关通知中对该评价的引用。
     *
     * @param id 待删除的评价ID
     * @return 删除成功的 {@link Result} 对象，包含成功提示信息
     */
    @Override
    @Transactional
    public Result deleteComment(Long id) {
        // 级联软删除关联的追评和评论
        commentAppendMapper.update(null,
                new LambdaUpdateWrapper<CommentAppend>()
                        .eq(CommentAppend::getMainCommentId, id)
                        .set(CommentAppend::getDeleted, 1));
        reviewCommentMapper.update(null,
                new LambdaUpdateWrapper<ReviewComment>()
                        .eq(ReviewComment::getReviewId, id)
                        .set(ReviewComment::getDeleted, 1));
        goodsCommentMapper.deleteById(id);
        // 清理通知中的评论引用
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewId, null)
                        .eq(Notification::getReviewId, id));
        log.info("管理员删除评价及关联数据: commentId={}", id);
        return Result.okMsg("删除成功");
    }

    /**
     * 批量删除指定的商品评价。遍历每个评价ID，先级联软删除关联的追评和评价评论，
     * 再批量删除评价本身，最后清理相关通知中对这些评价的引用。
     *
     * @param ids 待批量删除的评价ID列表
     * @return 批量删除成功的 {@link Result} 对象，包含成功提示信息
     */
    @Override
    @Transactional
    public Result batchDeleteComments(List<Long> ids) {
        // 批量级联软删除关联的追评和评价评论（一次 in 更新，避免循环逐条 update）
        commentAppendMapper.update(null,
                new LambdaUpdateWrapper<CommentAppend>()
                        .in(CommentAppend::getMainCommentId, ids)
                        .set(CommentAppend::getDeleted, 1));
        reviewCommentMapper.update(null,
                new LambdaUpdateWrapper<ReviewComment>()
                        .in(ReviewComment::getReviewId, ids)
                        .set(ReviewComment::getDeleted, 1));
        goodsCommentMapper.deleteByIds(ids);
        // 清理通知中的评论引用
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewId, null)
                        .in(Notification::getReviewId, ids));
        log.info("管理员批量删除评价及关联数据: count={}", ids.size());
        return Result.okMsg("批量删除成功");
    }

    /**
     * 删除指定的追评。直接物理删除追评记录。
     *
     * @param id 待删除的追评ID
     * @return 删除成功的 {@link Result} 对象，包含成功提示信息
     */
    @Override
    @Transactional
    public Result deleteAppend(Long id) {
        commentAppendMapper.deleteById(id);
        log.info("管理员删除追评: appendId={}", id);
        return Result.okMsg("删除成功");
    }

    /**
     * 批量删除指定的追评。直接物理批量删除追评记录。
     *
     * @param ids 待批量删除的追评ID列表
     * @return 批量删除成功的 {@link Result} 对象，包含成功提示信息
     */
    @Override
    @Transactional
    public Result batchDeleteAppends(List<Long> ids) {
        commentAppendMapper.deleteByIds(ids);
        log.info("管理员批量删除追评: count={}", ids.size());
        return Result.okMsg("批量删除成功");
    }

    /**
     * 删除指定的评价评论（回复）。先物理删除评论记录，再清理通知中对这条评论回复的引用。
     *
     * @param id 待删除的评价评论ID
     * @return 删除成功的 {@link Result} 对象，包含成功提示信息
     */
    @Override
    @Transactional
    public Result deleteReviewComment(Long id) {
        reviewCommentMapper.deleteById(id);
        // 清理通知中的评论回复引用
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewCommentId, null)
                        .eq(Notification::getReviewCommentId, id));
        log.info("管理员删除评价评论: reviewCommentId={}", id);
        return Result.okMsg("删除成功");
    }

    /**
     * 批量删除指定的评价评论（回复）。先物理批量删除评论记录，再清理通知中对这些评论回复的引用。
     *
     * @param ids 待批量删除的评价评论ID列表
     * @return 批量删除成功的 {@link Result} 对象，包含成功提示信息
     */
    @Override
    @Transactional
    public Result batchDeleteReviewComments(List<Long> ids) {
        reviewCommentMapper.deleteByIds(ids);
        // 清理通知中的评论回复引用
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewCommentId, null)
                        .in(Notification::getReviewCommentId, ids));
        log.info("管理员批量删除评价评论: count={}", ids.size());
        return Result.okMsg("批量删除成功");
    }

    /**
     * 解析逗号分隔的状态字符串为整数列表。
     *
     * @param statuses 逗号分隔的状态字符串，如 "0,3"，为 null 或空时返回 null
     * @return 状态整数列表，解析失败时返回空列表
     */
    private List<Integer> parseStatusList(String statuses) {
        if (statuses == null || statuses.trim().isEmpty()) {
            return null;
        }
        try {
            return Arrays.stream(statuses.split(","))
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            log.error("Invalid status format: {}", statuses, e);
            return Collections.emptyList();
        }
    }
}
