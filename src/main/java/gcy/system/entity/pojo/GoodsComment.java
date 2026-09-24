package gcy.system.entity.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品评论实体类，映射 goods_comment 表，记录用户对已购商品的评分与评价信息。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("goods_comment")
public class GoodsComment {

    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的订单ID
     */
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /**
     * 关联的订单项ID
     */
    private Long orderItemId;

    /**
     * 关联的商品ID
     */
    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    /**
     * 发表评论的用户ID
     */
    private Long userId;

    /**
     * 评分（星级）
     */
    @NotNull(message = "评分不能为空")
    private Integer score;

    /**
     * 评论文字内容
     */
    @NotBlank(message = "评价内容不能为空")
    private String content;

    /**
     * 评论配图URL
     */
    private String imgUrl;

    /**
     * 评论视频URL
     */
    private String videoUrl;

    /**
     * 是否匿名（0=否，1=是）
     */
    @JsonProperty("isAnonym")
    private Integer isAnonym;

    /**
     * 评论状态（如审核状态）
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
     * 是否有追加评论（0=否，1=是）
     */
    @JsonProperty("hasAppend")
    private Integer hasAppend;

    /**
     * 最近一次追加评论的时间
     */
    private LocalDateTime latestAppendTime;

    /**
     * 评论创建时间
     */
    private LocalDateTime createTime;

    /**
     * 逻辑删除标记（0=未删除，1=已删除）
     */
    @TableLogic
    private Integer deleted = 0;

    /**
     * 用户侧软删除标记（0=未删除，1=已删除）
     */
    private Integer userDeleted = 0;
}
