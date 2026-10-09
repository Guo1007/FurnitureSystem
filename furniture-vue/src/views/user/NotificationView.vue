<template>
  <div class="notification-page-new">
    <div class="page-breadcrumb">
      <button class="breadcrumb-back" @click="goBack" title="返回">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
      </button>
      <router-link to="/">首页</router-link>
      <span>/</span>
      <span class="current">消息通知</span>
      <el-button
        v-if="unreadCount > 0"
        type="primary"
        size="small"
        @click="handleMarkAllRead"
        style="margin-left: auto"
        >全部已读</el-button
      >
    </div>
    <div class="notif-container">
      <el-tabs v-model="activeTab" @tab-change="loadData">
        <el-tab-pane label="全部通知" name="all"></el-tab-pane>
      </el-tabs>

      <div v-if="loading" class="loading">加载中...</div>

      <template v-else>
        <div
          v-for="item in list"
          :key="item.id"
          class="notif-card"
          :class="{ unread: !item.isRead, disabled: isCommentDeleted(item) }"
          @click="handleRead(item)"
        >
          <div class="notif-left">
            <div class="notif-type-icon" :class="item.type || 'system'">
              {{ typeIcon(item.type) }}
            </div>
          </div>
          <div class="notif-body">
            <div class="notif-header">
              <span class="notif-title">{{ item.title }}</span>
              <el-tag
                v-if="isCommentDeleted(item)"
                size="small"
                type="info"
                >该内容已删除</el-tag
              >
              <el-tag
                v-else-if="!item.isRead"
                size="small"
                type="danger"
                effect="plain"
                >未读</el-tag
              >
            </div>
            <div class="notif-content">{{ item.content }}</div>
            <div class="notif-footer">
              <span class="notif-time">{{ formatTime(item.createTime) }}</span>
              <el-button
                type="danger"
                size="small"
                text
                @click.stop="handleDelete(item)"
              >
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </div>
        </div>

        <div v-if="list.length === 0 && !loading" class="empty">
          <el-empty description="暂无通知" />
        </div>

        <div class="pagination" v-if="total > size">
          <el-pagination
            v-model:current-page="current"
            :page-size="size"
            :total="total"
            layout="prev, pager, next"
            @current-change="onPageChange"
          />
        </div>
      </template>
    </div>

    <!-- 通知详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="通知详情"
      width="560px"
      :close-on-click-modal="true"
    >
      <div class="detail-container" v-if="detailItem">
        <div class="detail-header">
          <el-tag
            :type="
              detailItem.type === 'system'
                ? ''
                : detailItem.type === 'order'
                  ? 'warning'
                  : detailItem.type === 'comment_reply'
                    ? 'success'
                    : 'danger'
            "
            size="small"
            effect="plain"
          >
            {{
              detailItem.type === "system"
                ? "系统通知"
                : detailItem.type === "order"
                  ? "订单通知"
                  : detailItem.type === "comment_reply"
                    ? "回复通知"
                    : detailItem.type === "comment_reject"
                      ? "评价审核未通过"
                      : detailItem.type === "append_reject"
                        ? "追评审核未通过"
                        : detailItem.type === "reply_reject"
                          ? "回复审核未通过"
                          : "资料审核"
            }}
          </el-tag>
          <span class="detail-time">{{
            formatTime(detailItem.createTime)
          }}</span>
        </div>
        <h2 class="detail-title">{{ detailItem.title }}</h2>
        <el-divider />
        <div class="detail-content">{{ detailItem.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ArrowLeft, Delete } from "@element-plus/icons-vue";
import {
  getNotificationList,
  getUnreadCount,
  markAllAsRead,
  markAsRead,
  deleteMyNotification,
} from "@/api/notification.js";
import { ElMessage, ElMessageBox } from "element-plus";
import { formatTime } from "@/utils/format.js";
import { logger } from "@/utils/logger.js";
import { useBackNavigation } from '@/composables/useBackNavigation.js';

const router = useRouter();
const activeTab = ref("all");
const list = ref([]);
const total = ref(0);
const current = ref(1);
const size = ref(10);
const loading = ref(false);
const unreadCount = ref(0);

// 详情弹窗
const detailVisible = ref(false);
const detailItem = ref(null);

const loadData = async () => {
  loading.value = true;
  try {
    const res = await getNotificationList(current.value, size.value);
    if (res.success && res.data) {
      list.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (e) {
    logger.error(e);
  } finally {
    loading.value = false;
  }
};

const loadUnreadCount = async () => {
  try {
    const res = await getUnreadCount();
    if (res.success) unreadCount.value = res.data || 0;
  } catch (e) {}
};

const handleRead = async (item) => {
  if (isCommentDeleted(item)) return;
  if (!item.isRead) {
    await markAsRead(item.id);
    item.isRead = true;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
  }
  if (item.type === "comment_reply" && item.goodsId) {
    router.push({
      path: `/furniture/detail/${item.goodsId}`,
      query: { reviewId: item.reviewId, reviewCommentId: item.reviewCommentId },
    });
    return;
  }
  // 优惠券通知（管理员定向发放）：跳转到「我的卡券」，别打开详情弹窗
  if (item.type === "promotion") {
    router.push({ name: "MyCoupons" });
    return;
  }
  detailItem.value = item;
  detailVisible.value = true;
};

const isCommentDeleted = (item) => {
  return (
    item.type === "comment_reply" &&
    (item.reviewId === null || item.reviewId === undefined)
  );
};

const handleMarkAllRead = async () => {
  await markAllAsRead();
  list.value.forEach((item) => (item.isRead = true));
  unreadCount.value = 0;
};

const handleDelete = async (item) => {
  try {
    await ElMessageBox.confirm("确定要删除这条通知吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
  } catch {
    return;
  }
  try {
    await deleteMyNotification(item.id);
    list.value = list.value.filter((n) => n.id !== item.id);
    total.value = Math.max(0, total.value - 1);
    if (!item.isRead) {
      unreadCount.value = Math.max(0, unreadCount.value - 1);
    }
    ElMessage.success("已删除");
  } catch (e) {
    logger.error("删除通知失败:", e);
  }
};

const onPageChange = (page) => {
  current.value = page;
  loadData();
};

const { goBack } = useBackNavigation();

const typeIcon = (type) => {
  const map = {
    system: "📢",
    order: "📦",
    promotion: "🏷️",
    comment_reply: "💬",
    comment_reject: "❌",
    append_reject: "❌",
    reply_reject: "❌",
    profile_review: "✅",
  };
  return map[type] || "📢";
};

onMounted(() => {
  loadData();
  loadUnreadCount();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/notification-view.scss";
</style>
