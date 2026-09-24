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
 * 评论追加内容实体，映射 comment_append 表。
 * 记录用户对主评论的追加补充内容，每条追加可独立进行软删除管理。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("comment_append")
public class CommentAppend {

    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的主评论ID
     */
    @NotNull(message = "主评论ID不能为空")
    private Long mainCommentId;

    /**
     * 发表追加内容的用户ID
     */
    private Long userId;

    /**
     * 追加的文本内容
     */
    @NotBlank(message = "追评内容不能为空")
    private String appendContent;

    /**
     * 追加内容中的图片
     */
    private String appendImg;

    /**
     * 追加序号，标识第几次追加
     */
    private Integer appendNum;

    /**
     * 追加内容状态（如正常、隐藏等）
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
     * 追加时间
     */
    private LocalDateTime appendTime;

    /**
     * 管理员逻辑删除标记（0=未删除，1=已删除）
     */
    @TableLogic
    private Integer deleted = 0;

    /**
     * 用户端软删除标记，与管理员 deleted 互不影响（0=未删除，1=已删除）
     */
    private Integer userDeleted = 0;
}
