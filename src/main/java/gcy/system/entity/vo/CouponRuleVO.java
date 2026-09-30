package gcy.system.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 优惠券叠加规则配置视图对象（管理端展示用）。
 *
 * @author 郭名城
 * @date 2026-09-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "优惠券叠加规则配置")
public class CouponRuleVO {

    @Schema(description = "规则标识：max_stack_count / max_discount_ratio")
    private String ruleKey;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "规则值")
    private String ruleValue;

    @Schema(description = "是否启用该限制")
    private Boolean enabled;

    @Schema(description = "规则说明")
    private String remark;

    @Schema(description = "取值下限（前端校验用）")
    private Integer minValue;

    @Schema(description = "取值上限（前端校验用）")
    private Integer maxValue;

    @Schema(description = "单位：张 / %")
    private String unit;
}
