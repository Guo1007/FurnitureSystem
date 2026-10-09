import request from "./request";

export const createOrder = (data) => {
  return request({
    url: "/order/create",
    method: "post",
    data,
  });
};

/**
 * 下单试算：只算钱，不下单。
 * 前端所有优惠金额展示（购物车页 / 抽屉 / 详情页立即购买 / 选券弹窗）都走这个接口，
 * 前端不再保留任何抵扣算法 —— 后端是唯一实现，也就不会出现两边算得不一样。
 *
 * @param {{ itemList: Array, userCouponIds?: Array }} data
 */
export const estimateOrder = (data) => {
  return request({
    url: "/order/estimate",
    method: "post",
    data,
  });
};

export const getUserOrders = (params) => {
  return request({
    url: "/order/list",
    method: "get",
    params,
  });
};

export const getOrderDetail = (orderId) => {
  return request({
    url: `/order/detail/${orderId}`,
    method: "get",
  });
};

export const cancelOrder = (orderId) => {
  return request({
    url: `/order/cancel/${orderId}`,
    method: "put",
  });
};

// 注：原 /order/pay/{orderId} 直改订单状态的接口已下线，
// 订单置为「已支付」只能经由支付宝预下单 + 异步回调（prepayOrder / queryPayStatus）。

// 支付宝预下单：返回可自动提交的付款页面 HTML
export const prepayOrder = (orderId) => {
  return request({
    url: `/payment/prepay/${orderId}`,
    method: "post",
  });
};

// 主动查询订单支付状态（对账兜底，data 为 true 表示已支付）
export const queryPayStatus = (orderId) => {
  return request({
    url: `/payment/status/${orderId}`,
    method: "get",
  });
};

export function confirmReceipt(orderId) {
  return request({
    url: `/order/confirm/${orderId}`,
    method: "put",
  });
}

export function deleteOrder(orderId) {
  return request({
    url: `/order/${orderId}`,
    method: "delete",
  });
}

export const applyRefund = (data) => {
  return request({
    url: "/order/refund/apply",
    method: "post",
    data,
  });
};
