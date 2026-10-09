package gcy.system.controller;

import gcy.system.entity.dto.Result;
import gcy.system.service.ICouponRuleConfigService;
import gcy.system.service.ICouponService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端优惠券控制器，接口均需登录。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Tag(name = "优惠券", description = "用户端优惠券相关接口")
@RestController
@RequiredArgsConstructor
public class CouponController {

    private final ICouponService couponService;

    private final ICouponRuleConfigService couponRuleConfigService;

    /**
     * 可领优惠券列表（领券中心「可领券」Tab）。
     */
    @Operation(summary = "可领优惠券列表")
    @GetMapping("/user/coupons/claimable")
    public Result claimable() {
        return couponService.getClaimableList(UserHolder.getUser().getId());
    }

    /**
     * 我的优惠券列表（领券中心「我的券」Tab）。
     *
     * @param status 状态过滤：0-未用，1-已用，2-已过期；不传返回全部
     */
    @Operation(summary = "我的优惠券列表")
    @GetMapping("/user/coupons/mine")
    public Result mine(@Parameter(description = "状态过滤") @RequestParam(required = false) Integer status) {
        return couponService.getMyCoupons(UserHolder.getUser().getId(), status);
    }

    /**
     * 我的优惠券列表（分页），供「个人中心 - 我的卡券」页面使用。
     * <p>
     * 与 {@link #mine} 的区别：{@link #mine} 返回全量供选券弹窗展示评估，本接口按页取。
     *
     * @param status 状态过滤：0-未用，1-已用，2-已过期；不传返回全部
     * @param type   券类型过滤：1-满减，2-折扣，3-无门槛；不传返回全部
     */
    @Operation(summary = "我的优惠券列表（分页）")
    @GetMapping("/user/coupons/mine/page")
    public Result minePage(
            @Parameter(description = "状态过滤") @RequestParam(required = false) Integer status,
            @Parameter(description = "券类型过滤") @RequestParam(required = false) Integer type,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Long size) {
        return couponService.getMyCouponsPage(UserHolder.getUser().getId(), status, type, current, size);
    }

    /**
     * 领取优惠券。
     */
    @Operation(summary = "领取优惠券")
    @PostMapping("/user/coupons/{couponId}/claim")
    public Result claim(@Parameter(description = "优惠券ID") @PathVariable Long couponId) {
        return couponService.claim(UserHolder.getUser().getId(), couponId);
    }

    /**
     * 叠加规则（选券弹窗用），供前端约束可勾选张数并计算最优组合；最终以后端下单校验为准。
     *
     * @return maxStackCount（最大叠加张数）与 maxDiscountRatio（总抵扣上限比例，0~1）
     */
    @Operation(summary = "优惠券叠加规则")
    @GetMapping("/user/coupons/rules")
    public Result rules() {
        return Result.ok(couponRuleConfigService.rulesForUser());
    }
}