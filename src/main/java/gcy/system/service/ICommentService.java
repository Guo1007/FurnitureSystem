package gcy.system.service;

import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.CommentAppend;
import gcy.system.entity.pojo.GoodsComment;

/**
 * 评论服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface ICommentService {

    /**
     * 根据商品ID分页查询评论，按 userId 标记是否本人所发；current 从 1 开始。
     */
    Result getCommentsByGoodsId(Long goodsId, Long userId, Integer current, Integer size);

    /**
     * 根据订单ID查询评论，按 userId 做权限校验。
     */
    Result getCommentsByOrderId(Long orderId, Long userId);

    /**
     * 添加商品评论，仅限已购买商品。
     */
    Result addComment(GoodsComment comment, Long userId);

    /**
     * 追加评论。
     */
    Result appendComment(CommentAppend append, Long userId);

    /**
     * 删除评论，仅限本人。
     */
    Result deleteComment(Long commentId, Long userId);

    /**
     * 删除追评，仅限本人。
     */
    Result deleteAppend(Long appendId, Long userId);

    /**
     * 删除评论回复，仅限本人。
     */
    Result deleteReview(Long reviewId, Long userId);
}
