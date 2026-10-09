package gcy.system.service.admin;

import gcy.system.entity.dto.Result;


/**
 * 评论管理服务接口，覆盖评论、追评、审核评论三类对象的后台管理。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface ICommentManageService {

    /**
     * 分页获取评论列表。
     *
     * @param statuses 状态筛选，逗号分隔（如"0,3"），为null时查全部
     */
    Result getAllComments(Integer current, Integer size, String statuses);

    /**
     * 审核通过指定评论。
     */
    Result approveComment(Long commentId);

    /**
     * 驳回指定评论，并记录拒绝原因。
     */
    Result rejectComment(Long commentId, String rejectReason);

    /**
     * 分页获取追评列表。
     *
     * @param statuses 状态筛选，逗号分隔，为null时查全部
     */
    Result getAllAppends(Integer current, Integer size, String statuses);

    /**
     * 审核通过指定追评。
     */
    Result approveAppend(Long appendId);

    /**
     * 驳回指定追评，并记录拒绝原因。
     */
    Result rejectAppend(Long appendId, String rejectReason);

    /**
     * 分页获取审核评论列表。
     *
     * @param statuses 状态筛选，逗号分隔，为null时查全部
     */
    Result getAllReviewComments(Integer current, Integer size, String statuses);

    /**
     * 审核通过指定审核评论。
     */
    Result approveReviewComment(Long commentId);

    /**
     * 驳回指定审核评论，并记录拒绝原因。
     */
    Result rejectReviewComment(Long commentId, String rejectReason);

    /**
     * 删除指定评论。
     */
    Result deleteComment(Long id);

    /**
     * 批量删除评论。
     */
    Result batchDeleteComments(java.util.List<Long> ids);

    /**
     * 删除指定追评。
     */
    Result deleteAppend(Long id);

    /**
     * 批量删除追评。
     */
    Result batchDeleteAppends(java.util.List<Long> ids);

    /**
     * 删除指定审核评论。
     */
    Result deleteReviewComment(Long id);

    /**
     * 批量删除审核评论。
     */
    Result batchDeleteReviewComments(java.util.List<Long> ids);

    /**
     * 统计待处理的评论、追评及审核评论总数量。
     */
    Result getPendingCount();

    /**
     * 获取各状态数量统计，用于管理端 Tab 计数。
     * <p>
     * 返回结构：comment / append / reviewComment 各含 { all, pending, approved, rejected }。
     */
    Result getStatusCounts();
}
