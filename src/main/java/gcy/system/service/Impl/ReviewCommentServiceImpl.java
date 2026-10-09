package gcy.system.service.Impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Notification;
import gcy.system.entity.pojo.ReviewComment;
import gcy.system.entity.vo.ReviewCommentVO;
import gcy.system.exception.BusinessException;
import gcy.system.listener.AiReviewConsumer.AiReviewMessage;
import gcy.system.mapper.GoodsCommentMapper;
import gcy.system.mapper.NotificationMapper;
import gcy.system.mapper.ReviewCommentMapper;
import gcy.system.service.IReviewCommentService;
import gcy.system.utils.AfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评审评论服务实现类。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCommentServiceImpl implements IReviewCommentService {

    private final ReviewCommentMapper reviewCommentMapper;

    private final NotificationMapper notificationMapper;

    private final GoodsCommentMapper goodsCommentMapper;

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 查询评审下的评论列表，平铺结果组装为树形（子回复挂在根评论 children 下）。
     */
    @Override
    public Result getCommentsByReviewId(Long reviewId, Long userId) {
        List<ReviewCommentVO> allComments = reviewCommentMapper.selectByReviewId(reviewId, userId);
        List<ReviewCommentVO> tree = buildCommentTree(allComments);
        return Result.ok(tree);
    }

    /**
     * 新增评论或回复，初始状态为待审核。
     */
    @Override
    @Transactional
    public Result addComment(ReviewComment comment, Long userId) {
        if (comment.getReviewId() == null) {
            throw new BusinessException("评论目标不存在");
        }
        // 校验被评论的评价存在（防止伪造 reviewId）
        if (goodsCommentMapper.selectById(comment.getReviewId()) == null) {
            throw new BusinessException("评论目标不存在");
        }
        // 回复场景：被回复评论须存在，且作者与 replyToUserId 一致（防止伪造）
        if (comment.getReplyToCommentId() != null) {
            ReviewComment targetComment = reviewCommentMapper.selectById(comment.getReplyToCommentId());
            if (targetComment == null) {
                throw new BusinessException("被回复的评论不存在");
            }
            if (comment.getReplyToUserId() != null
                    && !targetComment.getUserId().equals(comment.getReplyToUserId())) {
                throw new BusinessException("回复目标用户不匹配");
            }
        }
        comment.setUserId(userId);
        comment.setStatus(0); // 待审核
        comment.setCreateTime(LocalDateTime.now());
        reviewCommentMapper.insert(comment);
        // 发送AI自动审核消息（异步，不阻塞用户请求）
        sendAiReviewMessage("review_comment", comment.getId());
        log.info("发表评论回复: reviewId={}, userId={}, status=待审核", comment.getReviewId(), userId);
        return Result.ok();
    }

    /**
     * 逻辑删除评论：置 user_deleted=1，并清理通知表中对该评论的引用。
     */
    @Override
    @Transactional
    public Result deleteComment(Long commentId, Long userId) {
        ReviewComment comment = reviewCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的评论");
        }
        reviewCommentMapper.update(null,
                new LambdaUpdateWrapper<ReviewComment>()
                        .eq(ReviewComment::getId, commentId)
                        .set(ReviewComment::getUserDeleted, 1));
        // 清理通知中的评论回复引用
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .set(Notification::getReviewCommentId, null)
                        .eq(Notification::getReviewCommentId, commentId));
        return Result.ok();
    }

    /**
     * 平铺评论列表组装为树形：无 replyToCommentId 的为根，其余挂到父评论 children。
     */
    private List<ReviewCommentVO> buildCommentTree(List<ReviewCommentVO> allComments) {
        Map<Long, List<ReviewCommentVO>> childrenMap = allComments.stream()
                .filter(c -> c.getReplyToCommentId() != null)
                .collect(Collectors.groupingBy(ReviewCommentVO::getReplyToCommentId));
        List<ReviewCommentVO> roots = new ArrayList<>();
        for (ReviewCommentVO c : allComments) {
            if (c.getReplyToCommentId() == null) {
                c.setChildren(childrenMap.getOrDefault(c.getId(), new ArrayList<>()));
                roots.add(c);
            }
        }
        return roots;
    }

    /**
     * 发送AI自动审核消息；失败仅记日志，不阻塞主流程。
     */
    private void sendAiReviewMessage(String type, Long id) {
        try {
            AiReviewMessage msg = new AiReviewMessage(type, id);
            String json = JSONUtil.toJsonStr(msg);
            // 走 AfterCommit 提交后再发 MQ：否则消费者查不到记录，回滚后消息也已发出
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
