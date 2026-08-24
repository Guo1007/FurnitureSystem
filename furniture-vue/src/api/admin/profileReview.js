import request from "@/api/request";

/**
 * 获取昵称审核列表
 */
export function getNicknameReviewList(page, size, status) {
  return request.get("/admin/profile-review/nickname/list", {
    params: { page, size, status },
  });
}

/**
 * 获取头像审核列表
 */
export function getIconReviewList(page, size, status) {
  return request.get("/admin/profile-review/icon/list", {
    params: { page, size, status },
  });
}

/**
 * 审核通过昵称
 */
export function approveNickname(userId) {
  return request.put(`/admin/profile-review/nickname/approve/${userId}`);
}

/**
 * 拒绝昵称
 */
export function rejectNickname(userId, reason) {
  return request.put(`/admin/profile-review/nickname/reject/${userId}`, { reason });
}

/**
 * 审核通过头像
 */
export function approveIcon(userId) {
  return request.put(`/admin/profile-review/icon/approve/${userId}`);
}

/**
 * 拒绝头像
 */
export function rejectIcon(userId, reason) {
  return request.put(`/admin/profile-review/icon/reject/${userId}`, { reason });
}

/**
 * 获取待审核数量
 */
export function getProfileReviewPendingCount() {
  return request.get("/admin/profile-review/pending-count");
}

/**
 * 获取昵称/头像审核各状态数量统计
 */
export function getProfileReviewStatusCounts() {
  return request.get("/admin/profile-review/status-counts");
}