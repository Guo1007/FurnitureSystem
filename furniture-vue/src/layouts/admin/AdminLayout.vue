<template>
  <div class="admin-layout">
    <!-- 顶部 -->
    <header class="admin-header">
      <div class="header-left">
        <span class="mobile-menu-btn" @click="sidebarOpen = !sidebarOpen"
          >☰</span
        >
        <router-link to="/" class="header-brand">
          <img
            v-if="sys.systemLogo"
            :src="imgUrl(sys.systemLogo)"
            class="brand-logo"
            alt=""
          />
          <span v-else class="brand-mark">木</span>
          <span class="brand-name">{{ sys.systemName }}</span>
          <span class="brand-dot">后台</span>
        </router-link>
      </div>
      <div class="header-right">
        <span class="admin-name">{{ adminName }}</span>
        <router-link to="/" class="header-action-btn" title="返回前台">
          <svg
            width="15"
            height="15"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
            <polyline points="9 22 9 12 15 12 15 22" />
          </svg>
          <span>返回前台</span>
        </router-link>
        <button class="header-action-btn logout-btn" @click="logout">
          <svg
            width="15"
            height="15"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
            <polyline points="16 17 21 12 16 7" />
            <line x1="21" y1="12" x2="9" y2="12" />
          </svg>
          <span>退出</span>
        </button>
      </div>
    </header>

    <div class="admin-body">
      <!-- 遮罩 -->
      <div
        class="sidebar-overlay"
        v-if="sidebarOpen"
        @click="sidebarOpen = false"
      ></div>

      <!-- 侧边栏 -->
      <aside class="admin-sidebar" :class="{ 'mobile-open': sidebarOpen }">
        <nav class="sidebar-nav">
          <template v-for="group in menuGroups" :key="group.title">
            <!-- 无标题分组：直接平铺 -->
            <template v-if="!group.title">
              <router-link
                v-for="menu in group.items"
                :key="menu.path"
                :to="menu.path"
                class="menu-item"
                :class="{ active: $route.path === menu.path }"
                @click="sidebarOpen = false"
              >
                <span class="menu-icon" v-html="menu.icon"></span>
                <span class="menu-text">{{ menu.name }}</span>
                <span v-if="menu.badge > 0" class="menu-badge">{{ menu.badge }}</span>
              </router-link>
            </template>

            <!-- 有标题分组：可折叠 -->
            <template v-else>
              <div
                class="menu-group-title"
                :class="{ open: expandedGroups[group.title] }"
                @click="toggleGroup(group.title)"
              >
                <span class="group-caret">▸</span>
                <span class="group-name">{{ group.title }}</span>
              </div>
              <div class="menu-group-items" :class="{ open: expandedGroups[group.title] }">
                <div class="menu-group-inner">
                  <router-link
                    v-for="menu in group.items"
                    :key="menu.path"
                    :to="menu.path"
                    class="menu-item"
                    :class="{ active: $route.path === menu.path }"
                    @click="sidebarOpen = false"
                  >
                    <span class="menu-icon" v-html="menu.icon"></span>
                    <span class="menu-text">{{ menu.name }}</span>
                    <span v-if="menu.badge > 0" class="menu-badge">{{ menu.badge }}</span>
                  </router-link>
                </div>
              </div>
            </template>
          </template>
        </nav>
      </aside>

      <!-- 内容区 -->
      <main class="admin-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
        <!-- 路由切换加载遮罩 -->
        <div v-if="routeLoading" class="route-loading">
          <div class="route-loading-spinner"></div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useSystemStore } from "@/stores/system.js";
import { imgUrl } from "@/utils/img.js";
import { useLogout } from "@/composables/useLogout.js";
import { getPendingOrderCount, getPendingRefundCount } from "@/api/admin/order.js";
import { getPendingCommentCount } from "@/api/admin/comment.js";
import { getProfileReviewPendingCount } from "@/api/admin/profileReview.js";

const router = useRouter();
const sys = useSystemStore();

const adminName = ref("管理员");
const sidebarOpen = ref(false);
const routeLoading = ref(false);

// SVG icons for each menu item, grouped by business domain
const menuGroups = ref([
  {
    title: "",
    items: [
      {
        path: "/admin/dashboard",
        name: "数据概览",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="8" width="7" height="4" rx="1"/><rect x="14" y="3" width="7" height="4" rx="1"/><rect x="3" y="13" width="7" height="8" rx="1"/></svg>',
        badge: 0,
      },
    ],
  },
  {
    title: "商品管理",
    items: [
      {
        path: "/admin/furniture",
        name: "家具管理",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 9V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v3"/><path d="M2 11v5a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5a2 2 0 0 0-4 0v2H6v-2a2 2 0 0 0-4 0z"/><path d="M4 18v2"/><path d="M20 18v2"/></svg>',
        badge: 0,
      },
      {
        path: "/admin/furniture_type",
        name: "分类管理",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>',
        badge: 0,
      },
      {
        path: "/admin/coupons",
        name: "优惠券管理",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2"/><line x1="9" y1="8" x2="9" y2="16"/><line x1="15" y1="8" x2="15" y2="16"/></svg>',
        badge: 0,
      },
    ],
  },
  {
    title: "交易管理",
    items: [
      {
        path: "/admin/orders",
        name: "订单管理",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1" ry="1"/></svg>',
        badge: 0,
      },
      {
        path: "/admin/after-sale",
        name: "售后处理",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><polyline points="3 3 3 8 8 8"/></svg>',
        badge: 0,
      },
    ],
  },
  {
    title: "用户与通知",
    items: [
      {
        path: "/admin/users",
        name: "用户管理",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
        badge: 0,
      },
      {
        path: "/admin/notification",
        name: "通知管理",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>',
        badge: 0,
      },
      {
        path: "/admin/notify-setting",
        name: "邮件配置",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>',
        badge: 0,
      },
    ],
  },
  {
    title: "系统管理",
    items: [
      {
        path: "/admin/site-content",
        name: "首页内容",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="4 17 10 11 14 15 20 9"/><polyline points="14 9 20 9 20 15"/><line x1="4" y1="21" x2="20" y2="21"/></svg>',
        badge: 0,
      },
      {
        path: "/admin/operation-logs",
        name: "操作日志",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>',
        badge: 0,
      },
    ],
  },
  {
    title: "内容审核",
    items: [
      {
        path: "/admin/comments",
        name: "商品评价",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>',
        badge: 0,
      },
      {
        path: "/admin/comment-appends",
        name: "追评",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>',
        badge: 0,
      },
      {
        path: "/admin/review-comments",
        name: "评价评论",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>',
        badge: 0,
      },
      {
        path: "/admin/nickname-review",
        name: "昵称审核",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>',
        badge: 0,
      },
      {
        path: "/admin/icon-review",
        name: "头像审核",
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>',
        badge: 0,
      },
    ],
  },
]);

// 分组折叠状态：默认展开，进入页面自动展开当前路由所在组
const expandedGroups = ref({});
const toggleGroup = (title) => {
  expandedGroups.value[title] = !expandedGroups.value[title];
};

let countTimer = null;
let removeBeforeGuard = null;
let removeAfterGuard = null;

// 在分组结构中查找菜单项并设置角标
const setMenuBadge = (path, count) => {
  for (const group of menuGroups.value) {
    const menu = group.items.find((m) => m.path === path);
    if (menu) {
      menu.badge = count;
      return;
    }
  }
};

const fetchPendingCounts = async () => {
  try {
    const [orderRes, commentRes, refundRes, profileRes] = await Promise.all([
      getPendingOrderCount(),
      getPendingCommentCount(),
      getPendingRefundCount(),
      getProfileReviewPendingCount(),
    ]);
    if (orderRes.success || orderRes.code === 200) {
      setMenuBadge("/admin/orders", orderRes.data?.pendingShipCount || 0);
    }
    if (commentRes.success || commentRes.code === 200) {
      const d = commentRes.data || {};
      setMenuBadge("/admin/comments", d.commentCount || 0);
      setMenuBadge("/admin/comment-appends", d.appendCount || 0);
      setMenuBadge("/admin/review-comments", d.reviewCommentCount || 0);
    }
    if (refundRes.success || refundRes.code === 200) {
      setMenuBadge("/admin/after-sale", refundRes.data?.pendingRefundCount || 0);
    }
    if (profileRes.success || profileRes.code === 200) {
      const pd = profileRes.data || {};
      setMenuBadge("/admin/nickname-review", pd.nicknameCount || 0);
      setMenuBadge("/admin/icon-review", pd.iconCount || 0);
    }
  } catch (e) {
    /* ignore */
  }
};

// 自动展开当前路由所在分组
const expandCurrentGroup = () => {
  const path = router.currentRoute.value.path;
  for (const group of menuGroups.value) {
    if (!group.title) continue;
    if (group.items.some((m) => m.path === path)) {
      expandedGroups.value[group.title] = true;
    } else if (expandedGroups.value[group.title] === undefined) {
      expandedGroups.value[group.title] = true; // 默认展开
    }
  }
};

onMounted(() => {
  sys.load();
  expandCurrentGroup();
  fetchPendingCounts();
  countTimer = setInterval(fetchPendingCounts, 60000);
  // 监听审核操作，实时刷新角标
  window.addEventListener("review-count-update", fetchPendingCounts);
  // 路由切换时显示加载遮罩，避免懒加载期间白屏/卡顿
  removeBeforeGuard = router.beforeEach(() => {
    routeLoading.value = true;
    return true;
  });
  removeAfterGuard = router.afterEach(() => {
    routeLoading.value = false;
  });
});

onBeforeUnmount(() => {
  if (countTimer) clearInterval(countTimer);
  if (removeBeforeGuard) removeBeforeGuard();
  if (removeAfterGuard) removeAfterGuard();
  window.removeEventListener("review-count-update", fetchPendingCounts);
});

const { logout } = useLogout();
</script>

<style scoped lang="scss">
@import "@/styles/views/admin-layout.scss";
</style>
