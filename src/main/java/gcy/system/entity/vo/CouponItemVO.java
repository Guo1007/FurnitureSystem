package gcy.system.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 可领优惠券列表项 VO，供领券中心「可领券」Tab 展示。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Data
@Schema(description = "可领优惠券项")
public class CouponItemVO {

    @Schema(description = "优惠券模板ID")
    private Long id;

    @Schema(description = "券名称")
    private String name;

    @Schema(description = "券类型：1-满减，2-折扣，3-无门槛")
    private Integer type;

    @Schema(description = "券类型文案（满减券/折扣券/无门槛券）")
    private String typeText;

    @Schema(description = "面额展示串（如 ¥50 / 8折）")
    private String amountText;

    @Schema(description = "使用门槛金额（元）")
    private BigDecimal minThreshold;

    @Schema(description = "适用范围文案（全场/分类）")
    private String scopeText;

    @Schema(description = "有效期文案（至 xx-xx / 领取后 N 天有效）")
    private String validText;

    @Schema(description = "每人限领数")
    private Integer perUserLimit;

    @Schema(description = "已领取数量")
    private Integer claimedCount;

    @Schema(description = "状态：0-可领，1-未开始领取，2-已结束/已领完，3-已领取")
    private Integer state;

    @Schema(description = "状态文案（按钮置灰时显示）")
    private String stateText;
}