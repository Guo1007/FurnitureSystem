package gcy.system.service;

import gcy.system.entity.dto.Result;

/**
 * 用户端优惠券服务接口。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
public interface ICouponService {

    /**
     * 查询当前用户可领优惠券列表（含未开始/已结束，隐藏不满足人群的券）。
     */
    Result getClaimableList(Long userId);

    /**
     * 查询当前用户已领取的券列表，可按状态过滤。
     *
     * @param userId 用户ID
     * @param status 状态过滤：0-未用，1-已用，2-已过期；null/空返回全部
     */
    Result getMyCoupons(Long userId, Integer status);

    /**
     * 领取优惠券（Redis Lua 原子扣减，写表失败回滚计数）。
     *
     * @param userId   用户ID
     * @param couponId 优惠券模板ID
     */
    Result claim(Long userId, Long couponId);
}