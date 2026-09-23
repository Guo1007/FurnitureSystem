import request from "@/api/request";

export const getOrderList = (params) => {
  return request({
    url: "/admin/order/list",
    method: "get",
    params,
  });
};

export function shipOrder(orderId) {
  return request({
    url: `/admin/order/ship/${orderId}`,
    method: "put",
  });
}

export function exportOrders() {
  return request({
    url: "/admin/order/export",
    method: "get",
    responseType: "blob",
  });
}

export function deleteOrder(orderId) {
  return request({
    url: `/admin/order/${orderId}`,
    method: "delete",
  });
}

export function batchDeleteOrders(ids) {
  return request({
    url: "/admin/order/batch",
    method: "delete",
    data: ids,
  });
}

export function getPendingOrderCount() {
  return request({
    url: "/admin/order/pending-count",
    method: "get",
  });
}

// 查询某订单的支付流水
export function getOrderPayments(orderId) {
  return request({
    url: `/admin/order/${orderId}/payment`,
    method: "get",
  });
}

export function approveRefund(orderId) {
  return request({
    url: `/admin/order/refund/approve/${orderId}`,
    method: "put",
  });
}

export function rejectRefund(orderId, data) {
  return request({
    url: `/admin/order/refund/reject/${orderId}`,
    method: "put",
    data,
  });
}

export function auditRefund(data) {
  return request({
    url: "/admin/order/refund/audit",
    method: "put",
    data,
  });
}

export function getPendingRefundCount() {
  return request({
    url: "/admin/order/refund/pending-count",
    method: "get",
  });
}

export function getRefundStatusCounts() {
  return request({
    url: "/admin/order/refund/status-counts",
    method: "get",
  });
}
