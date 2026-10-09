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
import gcy.system.utils.AfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论管理服务实现类：商品评价、追评、评价评论的审核与删除。
 * <p>
 * 审核通过评价评论时经 MQ 异步发回复通知。
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
     * 分页查询商品评价（含用户与商品信息）。
     */
    @Override
    public Result getAllComments(Integer current, Integer size, String statuses) {
        Page<AdminCommentVO> page = new Page<>(current, size);
        Page<AdminCommentVO> result = commentManageMapper.selectAllComments(page, parseStatusList(statuses));
        return Result.ok(result);
    }

    /**
     * 审核通过商品评价：状态置 1。
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
     * 审核拒绝商品评价：状态置 2，并站内通知评价人。
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
     * 分页查询追评数据。
     */
    @Override
    public Result getAllAppends(Integer current, Integer size, String statuses) {
        Page<AdminAppendVO> page = new Page<>(current, size);
        Page<AdminAppendVO> result = commentManageMapper.selectAllAppends(page, parseStatusList(statuses));
        return Result.ok(result);
    }

    /**
     * 审核通过追评：状态置 1。
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
     * 审核拒绝追评：状态置 2，并站内通知追评人。
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
     * 分页查询评价评论（回复）。
     */
    @Override
    public Result getAllReviewComments(Integer current, Integer size, String statuses) {
        Page<AdminReviewCommentVO> page = new Page<>(current, size);
        Page<AdminReviewCommentVO> result = commentManageMapper.selectAllReviewComments(page, parseStatusList(statuses));
        return Result.ok(result);
    }

    /**
     * 审核通过评价评论：状态置 1；非自回复时提交事务后经 MQ 发回复通知。
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
            AfterCommit.run(() -> sendReplyNotification(json));
        }
        return Result.ok();
    }

    /**
     * 发送评论回复通知到 MQ；失败仅记日志，不影响审核主流程。
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
     * 审核拒绝评价评论：状态置 2，并站内通知回复人。
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
     * 统计待审核的评价/追评/评价评论数量，返回键 commentCount、appendCount、reviewCommentCount。
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
     * 管理端 Tab 计数：评价/追评/回复三类各四态（全部/待审核/已通过/已拒绝）的数量。
     */
    @Override
    public Result getStatusCounts() {
        // 每表一条 GROUP BY status，3 次查询取回全部 12 个计数
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
     * 按 status 分组计数，一次 SQL 取回该表各状态数量；逻辑删除过滤由 MyBatis-Plus 自动追加。
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
     * 删除商品评价：级联软删除追评与评价评论，再删评价，最后清空通知中对该评价的引用。
     */
    @Override
    @Transactional
    public Result deleteComment(Long id) {
        commentAppendMapper.update(null,
                new LambdaUpdateWrapper<CommentAppend>()
                        .eq(CommentAppend::getMainCommentId, id)
                        .set(CommentAppend::getDeleted, 1));
        reviewCommentMapper.update(null,
                new LambdaUpdateWrapper<ReviewComment>()
                        .eq(ReviewComment::getReviewId, id)
                        .set(ReviewComment::getDeleted, 1));
        goodsCommentMapper.deleteById(id);
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewId, null)
                        .eq(Notification::getReviewId, id));
        log.info("管理员删除评价及关联数据: commentId={}", id);
        return Result.okMsg("删除成功");
    }

    /**
     * 批量删除商品评价：级联软删除追评与评价评论，再删评价，最后清空通知中相关引用。
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
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewId, null)
                        .in(Notification::getReviewId, ids));
        log.info("管理员批量删除评价及关联数据: count={}", ids.size());
        return Result.okMsg("批量删除成功");
    }

    /**
     * 删除追评（物理删除）。
     */
    @Override
    @Transactional
    public Result deleteAppend(Long id) {
        commentAppendMapper.deleteById(id);
        log.info("管理员删除追评: appendId={}", id);
        return Result.okMsg("删除成功");
    }

    /**
     * 批量删除追评（物理删除）。
     */
    @Override
    @Transactional
    public Result batchDeleteAppends(List<Long> ids) {
        commentAppendMapper.deleteByIds(ids);
        log.info("管理员批量删除追评: count={}", ids.size());
        return Result.okMsg("批量删除成功");
    }

    /**
     * 删除评价评论（物理删除），并清空通知中对该回复的引用。
     */
    @Override
    @Transactional
    public Result deleteReviewComment(Long id) {
        reviewCommentMapper.deleteById(id);
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewCommentId, null)
                        .eq(Notification::getReviewCommentId, id));
        log.info("管理员删除评价评论: reviewCommentId={}", id);
        return Result.okMsg("删除成功");
    }

    /**
     * 批量删除评价评论（物理删除），并清空通知中相关引用。
     */
    @Override
    @Transactional
    public Result batchDeleteReviewComments(List<Long> ids) {
        reviewCommentMapper.deleteByIds(ids);
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewCommentId, null)
                        .in(Notification::getReviewCommentId, ids));
        log.info("管理员批量删除评价评论: count={}", ids.size());
        return Result.okMsg("批量删除成功");
    }

    /**
     * 解析逗号分隔的状态串；空返回 null，格式非法返回空列表。
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
