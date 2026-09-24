import request from "./request";

export const createOrder = (data) => {
  return request({
    url: "/order/create",
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

export const payOrder = (orderId) => {
  return request({
    url: `/order/pay/${orderId}`,
    method: "put",
  });
};

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
