package gcy.system.controller;

import gcy.system.entity.dto.Result;
import gcy.system.service.ICouponService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端优惠券控制器。
 * <p>提供可领券列表、我的券、领取三个接口，均需登录。</p>
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Tag(name = "优惠券", description = "用户端优惠券相关接口")
@RestController
@RequiredArgsConstructor
public class CouponController {

    private final ICouponService couponService;

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
     * 领取优惠券。
     */
    @Operation(summary = "领取优惠券")
    @PostMapping("/user/coupons/{couponId}/claim")
    public Result claim(@Parameter(description = "优惠券ID") @PathVariable Long couponId) {
        return couponService.claim(UserHolder.getUser().getId(), couponId);
    }
}