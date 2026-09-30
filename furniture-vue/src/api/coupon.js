import request from "./request";

/** 可领优惠券列表 */
export const getClaimableCoupons = () =>
  request.get("/user/coupons/claimable");

/** 我的优惠券列表 */
export const getMyCoupons = (status) =>
  request.get("/user/coupons/mine", { params: { status } });

/** 领取优惠券 */
export const claimCoupon = (couponId) =>
  request.post(`/user/coupons/${couponId}/claim`);

/** 优惠券叠加规则（最大叠加张数 / 总抵扣上限比例），选券弹窗用 */
export const getCouponRules = () => request.get("/user/coupons/rules");