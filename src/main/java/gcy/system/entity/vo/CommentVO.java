package gcy.system.entity.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论视图对象，用于前端展示评论数据，包含评论基本信息、评分、
 * 匿名状态、追加评论列表等相关展示字段。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO {

    /**
     * 评论ID
     */
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 商品ID
     */
    private Long goodsId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 用户头像URL
     */
    private String userAvatar;

    /**
     * 评分
     */
    private Integer score;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片URL
     */
    private String imgUrl;

    /**
     * 评论视频URL
     */
    private String videoUrl;

    /**
     * 是否匿名：1-是，0-否
     */
    @JsonProperty("isAnonym")
    private Integer isAnonym;

    /**
     * 评论状态
     */
    private Integer status;

    /**
     * 是否有追加评论：1-是，0-否
     */
    @JsonProperty("hasAppend")
    private Integer hasAppend;

    /**
     * 追加评论列表
     */
    private List<CommentAppendVO> appendList;

    /**
     * 该条评价下的评论（回复）总数，用于列表页角标。
     * <p>
     * 服务端一次 GROUP BY 批量算好：此前前端拿到评价列表后要对**每条**评价
     * 单独请求一次 {@code /review-comment/list/{id}} 才能显示角标，
     * 详情页固定取 100 条评价就是 100 次串行 HTTP。
     * 口径与「展开评论区后实际渲染的条数」一致（排除已逻辑删除/用户自删/未过审）。
     * </p>
     */
    private Integer commentCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否删除：1-已删除，0-未删除
     */
    private int deleted;

    /**
     * 用户是否删除：1-已删除，0-未删除
     */
    private int userDeleted;
}
