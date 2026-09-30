package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.service.ICouponRuleConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端优惠券叠加规则配置控制器，挂在 /admin/coupon-rule。
 * <p>
 * 将下单时的券叠加限制（最大叠加张数、总抵扣上限比例）开放为后台可配置项，
 * 避免调整促销规则需要改代码重新发版。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-30
 */
@Tag(name = "优惠券规则配置", description = "优惠券叠加规则配置")
@RestController
@RequestMapping("/admin/coupon-rule")
@RequiredArgsConstructor
public class CouponRuleController {

    private final ICouponRuleConfigService couponRuleConfigService;

    /**
     * 查询全部叠加规则配置。
     *
     * @return 规则列表
     */
    @Operation(summary = "查询优惠券叠加规则")
    @GetMapping
    public Result list() {
        return couponRuleConfigService.listRules();
    }

    /**
     * 保存单条规则配置。
     *
     * @param dto 保存请求体
     * @return 保存结果
     */
    @OperationLog("保存优惠券叠加规则")
    @Operation(summary = "保存优惠券叠加规则")
    @PutMapping
    public Result save(@Parameter(description = "请求体") @Valid @RequestBody SaveRuleDTO dto) {
        return couponRuleConfigService.saveRule(dto.getRuleKey(), dto.getRuleValue(), dto.getEnabled());
    }

    /**
     * 保存规则请求体。
     */
    @Data
    public static class SaveRuleDTO {
        /**
         * 规则标识：max_stack_count-最大叠加张数、max_discount_ratio-总抵扣上限百分比
         */
        @jakarta.validation.constraints.NotBlank(message = "规则标识不能为空")
        private String ruleKey;
        /**
         * 规则值（整数字符串）
         */
        @jakarta.validation.constraints.NotBlank(message = "规则值不能为空")
        private String ruleValue;
        /**
         * 是否启用该限制
         */
        private Boolean enabled;
    }
}
