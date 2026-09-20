import request from "@/api/request";

/** 优惠券列表 */
export function getCouponList(params) {
  return request({ url: "/admin/coupons/list", method: "get", params });
}

/** 详情 */
export function getCouponInfo(id) {
  return request({ url: `/admin/coupons/info/${id}`, method: "get" });
}

/** 新增 */
export function addCoupon(data) {
  return request({ url: "/admin/coupons/add", method: "post", data });
}

/** 修改 */
export function updateCoupon(data) {
  return request({ url: "/admin/coupons/update", method: "put", data });
}

/** 删除 */
export function deleteCoupon(id) {
  return request({ url: `/admin/coupons/delete/${id}`, method: "delete" });
}

/** 切换启用状态 */
export function toggleCoupon(id) {
  return request({ url: `/admin/coupons/toggle/${id}`, method: "put" });
}