package gcy.system.entity.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端优惠券表单 DTO，新增/编辑复用。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Data
@Schema(description = "优惠券表单")
public class AdminCouponFormDTO {

    @Schema(description = "折扣ID（新增为空）")
    private Long id;

    @NotBlank(message = "券名称必须填写")
    @Schema(description = "券名称")
    private String name;

    @NotNull(message = "券类型必须选择")
    @Min(value = 1, message = "券类型不合法")
    @Max(value = 3, message = "券类型不合法")
    @Schema(description = "券类型：1-满减券，2-折扣券，3-无门槛券")
    private Integer type;

    @DecimalMin(value = "0", message = "门槛不能为负")
    @Schema(description = "使用门槛金额")
    private BigDecimal minThreshold;

    @DecimalMin(value = "0", message = "面额不能为负")
    @Schema(description = "减免金额（满减/无门槛券）")
    private BigDecimal amount;

    @DecimalMin(value = "0", message = "折扣率不能为负")
    @Schema(description = "折扣率（折扣券，0.80=8折）")
    private BigDecimal discount;

    @DecimalMin(value = "0", message = "折扣上限不能为负")
    @Schema(description = "折扣券最高优惠上限")
    private BigDecimal capAmount;

    @Schema(description = "适用范围：0-全场，1-按分类")
    private Integer scope;

    @Schema(description = "分类ID（scope=1时）")
    private Long typeId;

    @Min(value = 0, message = "发放总量不能为负")
    @Schema(description = "发放总量（空=不限）")
    private Integer totalCount;

    @NotNull(message = "每人限领数必须填写")
    @Min(value = 1, message = "每人限领至少为1")
    @Schema(description = "每人限领数")
    private Integer perUserLimit;

    @Schema(description = "领取开始时间（空=不限）")
    private LocalDateTime claimStart;

    @Schema(description = "领取结束时间（空=不限）")
    private LocalDateTime claimEnd;

    @NotNull(message = "有效期模式必须选择")
    @Min(value = 1, message = "有效期模式不合法")
    @Max(value = 2, message = "有效期模式不合法")
    @Schema(description = "有效期模式：1-固定有效期，2-领取后N天")
    private Integer validType;

    @Schema(description = "固定有效期开始（validType=1）")
    private LocalDateTime validStart;

    @Schema(description = "固定有效期结束（validType=1）")
    private LocalDateTime validEnd;

    @Min(value = 1, message = "有效天数至少为1")
    @Schema(description = "领取后有效天数（validType=2）")
    private Integer validDays;

    @Schema(description = "领取人群：0-不限，1-新用户，2-老用户")
    private Integer targetType;

    @Min(value = 1, message = "人群判定天数至少为1")
    @Schema(description = "新/老用户判定天数阈值")
    private Integer targetDays;

    @Schema(description = "状态：0-停用，1-启用")
    private Integer status;

    @Schema(description = "是否可叠加使用：0-不可叠加，1-可叠加")
    private Integer stackable;
}