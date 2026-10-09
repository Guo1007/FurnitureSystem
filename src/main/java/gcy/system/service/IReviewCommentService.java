package gcy.system.service;

import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.ReviewComment;

/**
 * 审核评论服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IReviewCommentService {

    /**
     * 查询某审核记录下的所有评论。
     */
    Result getCommentsByReviewId(Long reviewId, Long userId);

    /**
     * 新增一条评论。
     */
    Result addComment(ReviewComment comment, Long userId);

    /**
     * 删除评论。
     */
    Result deleteComment(Long commentId, Long userId);
}
