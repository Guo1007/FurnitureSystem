package gcy.system.entity.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论实体，映射 review_comment 表，用于存储用户发表的评论数据，
 * 支持评论回复、逻辑删除、用户级软删除等功能。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("review_comment")
public class ReviewComment {

    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的评价/评测ID
     */
    @NotNull(message = "关联评价ID不能为空")
    private Long reviewId;

    /**
     * 发表评论的用户ID
     */
    private Long userId;

    /**
     * 评论内容
     */
    @NotBlank(message = "评论内容不能为空")
    private String content;

    /**
     * 被回复的用户ID
     */
    private Long replyToUserId;

    /**
     * 被回复的评论ID
     */
    private Long replyToCommentId;

    /**
     * 评论状态
     */
    private Integer status;

    /**
     * AI审核拒绝原因
     */
    private String aiRejectReason;

    /**
     * 人工审核拒绝原因
     */
    private String manualRejectReason;

    /**
     * 评论创建时间
     */
    private LocalDateTime createTime;

    /**
     * 逻辑删除标记（0-未删除，1-已删除）
     */
    @TableLogic
    private Integer deleted = 0;

    /**
     * 用户级软删除标记（0-未删除，1-已删除）
     */
    private Integer userDeleted = 0;
}
