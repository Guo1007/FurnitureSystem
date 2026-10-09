<template>
  <div class="notification-bell" ref="bellRef">
    <el-badge
      :value="unreadCount"
      :hidden="unreadCount === 0"
      class="badge"
      :max="99"
    >
      <el-button class="bell-btn" @click="toggleDropdown">
        <el-icon :size="22">
          <Bell />
        </el-icon>
        <span class="bell-text" v-if="unreadCount > 0">{{ unreadCount }}</span>
      </el-button>
    </el-badge>

    <transition name="el-zoom-in-top">
      <div v-if="showDropdown" class="notification-dropdown">
        <div class="dropdown-header">
          <span class="title">消息通知</span>
          <div class="header-actions">
            <el-button
              v-if="unreadCount > 0"
              type="primary"
              size="small"
              plain
              @click="handleMarkAllRead"
            >
              全部已读
            </el-button>
          </div>
        </div>
        <div class="dropdown-body">
          <div v-if="loading" class="loading">
            <el-icon class="loading-icon" :size="18">
              <Loading />
            </el-icon>
            加载中...
          </div>
          <div v-else-if="list.length === 0" class="empty">暂无通知</div>
          <div
            v-for="item in list"
            :key="item.id"
            class="notif-item"
            :class="{ unread: !item.isRead, deleted: isCommentDeleted(item) }"
            @click="handleClick(item)"
          >
            <div class="notif-dot" v-if="!item.isRead"></div>
            <div class="notif-type-icon">
              {{ typeIcon(item.type) }}
            </div>
            <div class="notif-content">
              <div class="notif-title">{{ item.title }}</div>
              <div class="notif-desc">{{ item.content }}</div>
              <div class="notif-meta">
                <span class="notif-time">{{
                  formatTime(item.createTime)
                }}</span>
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
                  >新</el-tag
                >
              </div>
            </div>
            <el-button
              class="delete-btn"
              size="small"
              text
              type="danger"
              @click.stop="handleDelete(item)"
            >
              <el-icon :size="14"><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="dropdown-footer">
          <el-button type="text" size="small" @click="goToAll"
            >查看全部通知</el-button
          >
        </div>
      </div>
    </transition>

    <!-- 通知详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="通知详情"
      width="520px"
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
                  : 'success'
            "
            size="small"
            effect="plain"
          >
            {{ typeLabel(detailItem.type) }}
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
import { onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Bell, Loading, Delete } from "@element-plus/icons-vue";
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

const router = useRouter();
const bellRef = ref(null);
const showDropdown = ref(false);
const unreadCount = ref(0);
const list = ref([]);
const total = ref(0);
const loading = ref(false);

// 详情弹窗
const detailVisible = ref(false);
const detailItem = ref(null);

let timer = null;

const onClickOutside = (e) => {
  if (bellRef.value && !bellRef.value.contains(e.target)) {
    showDropdown.value = false;
  }
};

const loadUnreadCount = async () => {
  try {
    const res = await getUnreadCount();
    if (res.success) {
      unreadCount.value = res.data || 0;
    }
  } catch (e) {
    // ignore
  }
};

const loadRecent = async () => {
  loading.value = true;
  try {
    const res = await getNotificationList(1, 5);
    if (res.success && res.data) {
      list.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (e) {
    // ignore
  } finally {
    loading.value = false;
  }
};

const toggleDropdown = () => {
  showDropdown.value = !showDropdown.value;
  if (showDropdown.value) {
    loadRecent();
  }
};

const handleClick = async (item) => {
  if (isCommentDeleted(item)) return;
  if (!item.isRead) {
    await markAsRead(item.id);
    unreadCount.value = Math.max(0, unreadCount.value - 1);
    item.isRead = true;
  }
  // 评论回复通知：跳转到对应商品详情页
  if (item.type === "comment_reply" && item.goodsId) {
    showDropdown.value = false;
    router.push({
      path: `/furniture/detail/${item.goodsId}`,
      query: { reviewId: item.reviewId, reviewCommentId: item.reviewCommentId },
    });
    return;
  }
  // 优惠券通知（管理员定向发放）：跳转到「我的卡券」
  // 通知表没有链接字段，跳转只能按 type 约定 —— 如果将来 promotion 类型
  // 用于别的促销内容，这里要改成分得更细的类型，否则会跳错地方。
  if (item.type === "promotion") {
    showDropdown.value = false;
    router.push({ name: "MyCoupons" });
    return;
  }
  // 打开详情弹窗
  detailItem.value = item;
  detailVisible.value = true;
  showDropdown.value = false;
};

const handleMarkAllRead = async () => {
  try {
    await markAllAsRead();
    list.value.forEach((item) => (item.isRead = true));
    unreadCount.value = 0;
  } catch (e) {
    // ignore
  }
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
    if (!item.isRead) {
      unreadCount.value = Math.max(0, unreadCount.value - 1);
    }
    ElMessage.success("已删除");
  } catch (e) {
    logger.error("删除通知失败:", e);
  }
};

const goToAll = () => {
  showDropdown.value = false;
  router.push("/notification");
};

const typeIcon = (type) => {
  const map = {
    system: "📢",
    order: "📦",
    promotion: "🏷️",
    comment_reply: "💬",
  };
  return map[type] || "📢";
};

const typeLabel = (type) => {
  const map = {
    system: "系统通知",
    order: "订单通知",
    promotion: "促销通知",
    comment_reply: "回复通知",
  };
  return map[type] || "通知";
};

const isCommentDeleted = (item) => {
  return (
    item.type === "comment_reply" &&
    (item.reviewId === null || item.reviewId === undefined)
  );
};

onMounted(() => {
  document.addEventListener("click", onClickOutside);
  // 未登录访客不启动轮询：未读数是需鉴权接口，游客每 30 秒必然吃一次 401，
  // 还会触发「登录已过期」的误导性提示并反复清空 localStorage。
  if (!localStorage.getItem("token")) return;
  loadUnreadCount();
  timer = setInterval(loadUnreadCount, 30000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
  document.removeEventListener("click", onClickOutside);
});
</script>

<style scoped lang="scss">
@import "@/styles/views/notification-bell.scss";
</style>
