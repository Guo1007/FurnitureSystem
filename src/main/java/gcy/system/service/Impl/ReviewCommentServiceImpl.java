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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评审评论服务实现类。
 * <p>
 * 负责评审评论和回复的查询、新增、删除等业务逻辑的具体实现，
 * 包括评论树形结构的构建以及评论删除时关联通知引用的清理。
 * </p>
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
     * 根据评审ID获取评论列表，并按树形结构组织后返回。
     * <p>
     * 先查询该评审下所有评论（含回复），再通过 {@link #buildCommentTree(List)}
     * 将平铺列表转换为树形结构，根评论的 children 字段中包含其下所有子回复。
     * </p>
     *
     * @param reviewId 评审ID，用于查询关联的评论
     * @param userId   当前登录用户的ID，用于判断评论是否属于当前用户
     * @return 包含树形评论列表的Result对象
     */
    @Override
    public Result getCommentsByReviewId(Long reviewId, Long userId) {
        List<ReviewCommentVO> allComments = reviewCommentMapper.selectByReviewId(reviewId, userId);
        List<ReviewCommentVO> tree = buildCommentTree(allComments);
        return Result.ok(tree);
    }

    /**
     * 添加一条新的评论或回复。
     * <p>
     * 校验评论所属评审是否存在后，设置发布用户、初始状态（待审核）和创建时间，
     * 然后插入数据库并记录操作日志。
     * </p>
     *
     * @param comment 评论实体，包含评论内容、所属评审ID以及可选的回复目标评论ID
     * @param userId  发表评论的用户ID
     * @return 操作结果
     * @throws BusinessException 当评论关联的评审ID为null时抛出，提示"评论目标不存在"
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
        // 回复场景校验：被回复评论必须存在，且其作者与声明的回复目标一致（防止伪造 replyToUserId）
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
     * 逻辑删除指定的评论。
     * <p>
     * 首先校验评论是否存在以及操作用户是否为评论作者，校验通过后将评论标记为
     * 用户已删除状态，同时清理通知表中对该评论的引用，避免后续展示无效数据。
     * </p>
     *
     * @param commentId 要删除的评论ID
     * @param userId    当前操作用户ID，用于校验是否为评论作者
     * @return 操作结果
     * @throws BusinessException 当评论不存在或当前用户不是评论作者时抛出
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
     * 将扁平的评论列表转换为树形结构。
     * <p>
     * 遍历所有评论，按 {@code replyToCommentId} 分组建立父子关系：
     * 没有父评论ID的作为根节点（顶层评论），有父评论ID的挂载到对应父评论的
     * children 列表中，最终返回只包含根评论的列表。
     * </p>
     *
     * @param allComments 所有评论的平铺列表，包含根评论和子回复
     * @return 树形结构的根评论列表，子评论挂载在各自的children字段中
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
     * 发送AI自动审核消息到MQ。
     * <p>
     * 发送失败仅记录日志，不阻塞主流程（审核为异步增强，非关键路径）。
     * </p>
     *
     * @param type 审核类型
     * @param id   对应记录的ID
     */
    private void sendAiReviewMessage(String type, Long id) {
        try {
            AiReviewMessage msg = new AiReviewMessage(type, id);
            String json = JSONUtil.toJsonStr(msg);
            // 与 CommentServiceImpl 一致：事务提交后再发 MQ，避免消费者查不到记录 / 回滚后消息已发出
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
