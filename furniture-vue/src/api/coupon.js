import request from "./request";

/** 可领优惠券列表 */
export const getClaimableCoupons = () =>
  request.get("/user/coupons/claimable");

/**
 * 我的优惠券列表（**全量**）。
 * 选券弹窗用：它要展示并评估用户的每一张券，所以必须拿全，不能分页。
 * 「我的卡券」页请用下面的 getMyCouponsPage。
 */
export const getMyCoupons = (status) =>
  request.get("/user/coupons/mine", { params: { status } });

/**
 * 我的优惠券列表（分页），供「个人中心 - 我的卡券」使用。
 *
 * @param {{ status?: number, type?: number, current?: number, size?: number }} params
 *   type：1-满减，2-折扣，3-无门槛；不传返回全部
 */
export const getMyCouponsPage = (params) =>
  request.get("/user/coupons/mine/page", { params });

/** 领取优惠券 */
export const claimCoupon = (couponId) =>
  request.post(`/user/coupons/${couponId}/claim`);

/** 优惠券叠加规则（最大叠加张数 / 总抵扣上限比例），选券弹窗用 */
export const getCouponRules = () => request.get("/user/coupons/rules");