package gcy.system.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的优惠券列表项 VO，供领券中心「我的券」Tab 展示。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Data
@Schema(description = "我的优惠券项")
public class UserCouponVO {

    @Schema(description = "用户领取记录ID")
    private Long userCouponId;

    @Schema(description = "优惠券模板ID")
    private Long couponId;

    @Schema(description = "券名称")
    private String name;

    @Schema(description = "券类型：1-满减，2-折扣，3-无门槛")
    private Integer type;

    @Schema(description = "面额展示串（如 ¥50 / 8折）")
    private String amountText;

    @Schema(description = "适用范围文案（全场/分类）")
    private String scopeText;

    @Schema(description = "使用门槛金额（元）")
    private BigDecimal minThreshold;

    @Schema(description = "减免金额（满减/无门槛券）")
    private BigDecimal amount;

    @Schema(description = "折扣率（折扣券，0.8=8折）")
    private BigDecimal discount;

    @Schema(description = "折扣券最高优惠上限")
    private BigDecimal capAmount;

    @Schema(description = "状态：0-未用，1-已用，2-已过期")
    private Integer status;

    @Schema(description = "状态文案（未使用/已使用/已过期）")
    private String statusText;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "领取时间")
    private LocalDateTime gotTime;

    @Schema(description = "使用时间")
    private LocalDateTime useTime;
}