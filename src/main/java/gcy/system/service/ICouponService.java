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
     * 分页查询当前用户已领取的券，供「个人中心 - 我的卡券」页面使用。
     * <p>
     * 为什么不给 {@link #getMyCoupons} 直接加分页参数：选券弹窗需要**全量**券列表
     * （它要展示并评估用户的每一张券），而分页接口只能给出当前页，两者取数语义不同，
     * 共用同一个返回结构会逼调用方去猜 shape。因此保留全量接口，另开分页接口。
     * </p>
     *
     * @param userId  用户ID
     * @param status  状态过滤：0-未用，1-已用，2-已过期；null 返回全部
     * @param type    券类型过滤：1-满减，2-折扣，3-无门槛；null 返回全部
     * @param current 页码，从 1 开始
     * @param size    每页条数
     * @return 分页的用户券视图对象
     */
    Result getMyCouponsPage(Long userId, Integer status, Integer type, Long current, Long size);

    /**
     * 领取优惠券（Redis Lua 原子扣减，写表失败回滚计数）。
     *
     * @param userId   用户ID
     * @param couponId 优惠券模板ID
     */
    Result claim(Long userId, Long couponId);
}