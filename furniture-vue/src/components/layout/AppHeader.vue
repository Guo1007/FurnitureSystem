<template>
  <header class="app-header" :class="{ 'is-scrolled': scrolled }">
    <div class="header-inner">
      <!-- Logo -->
      <router-link to="/" class="header-logo">
        <img
          v-if="sys.systemLogo"
          :src="imgUrl(sys.systemLogo)"
          class="logo-img"
          alt=""
        />
        <span v-else class="logo-mark">木</span>
        <div class="logo-text">
          <span class="logo-name">{{ sys.systemName }}</span>
          <span class="logo-tagline">{{ sys.systemTagline }}</span>
        </div>
      </router-link>

      <!-- Navigation -->
      <nav class="header-nav" ref="navRef">
        <router-link
          to="/"
          class="nav-link"
          :class="{ active: $route.path === '/' }"
        >
          首页
        </router-link>
        <div class="nav-link has-mega">
          <span class="nav-label">全部商品</span>
          <svg width="12" height="12" viewBox="0 0 12 12" class="nav-chevron">
            <path
              d="M3 4.5l3 3 3-3"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <!-- Mega Menu -->
          <div class="mega-menu">
            <div class="mega-inner">
              <div class="mega-categories">
                <router-link
                  v-for="cat in categories"
                  :key="cat.id"
                  :to="`/type/${cat.id}`"
                  class="mega-cat-item"
                >
                  <span class="mega-cat-icon">
                    <img
                      v-if="isImgPath(cat.icon)"
                      :src="imgUrl(cat.icon)"
                      class="mega-cat-img"
                    />
                    <span v-else>{{ cat.icon || "🪑" }}</span>
                  </span>
                  <span class="mega-cat-name">{{ cat.name }}</span>
                </router-link>
              </div>
            </div>
          </div>
        </div>

        <router-link
          to="/ai-chat"
          class="nav-link"
          :class="{ active: $route.path === '/ai-chat' }"
        >
          智能客服
        </router-link>
      </nav>

      <!-- Right actions -->
      <div class="header-actions">
        <!-- Search -->
        <div class="header-search" :class="{ 'is-open': searchOpen }">
          <button class="icon-btn" @click="toggleSearch" aria-label="搜索">
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            >
              <circle cx="11" cy="11" r="8" />
              <path d="M21 21l-4.35-4.35" />
            </svg>
          </button>
          <Transition name="search-slide">
            <form
              v-if="searchOpen"
              class="search-form"
              @submit.prevent="doSearch"
            >
              <input
                ref="searchInput"
                v-model="searchText"
                type="text"
                placeholder="搜索家具..."
                @keydown.escape="searchOpen = false"
              />
            </form>
          </Transition>
        </div>

        <!-- Notifications（仅登录显示） -->
        <NotificationBell v-if="isLoggedIn" />

        <!-- Favorites -->
        <button
          class="icon-btn"
          @click="handleFavoritesClick"
          aria-label="收藏"
        >
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
            />
          </svg>
        </button>

        <!-- Cart -->
        <button
          class="icon-btn cart-btn"
          @click="router.push('/cart')"
          aria-label="购物车"
        >
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <circle cx="9" cy="21" r="1" />
            <circle cx="20" cy="21" r="1" />
            <path
              d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"
            />
          </svg>
          <span class="cart-badge" v-if="cartStore.totalCount > 0">{{
            cartStore.totalCount
          }}</span>
        </button>

        <!-- 未登录：登录/注册入口 -->
        <template v-if="!isLoggedIn">
          <router-link
            :to="{ path: '/login', query: redirectQuery }"
            class="header-auth-btn login-btn"
          >登录</router-link>
          <router-link
            :to="{ path: '/register', query: redirectQuery }"
            class="header-auth-btn register-btn"
          >注册</router-link>
        </template>

        <!-- User dropdown（已登录） -->
        <div v-else class="user-menu" @click="toggleUserMenu" ref="userMenuRef">
          <div class="user-avatar-sm">
            <img
              :src="userAvatar"
              alt=""
              @error="(e) => (e.target.src = '/images/default-avatar.png')"
            />
          </div>
          <Transition name="menu-fade">
            <div class="user-dropdown" v-if="userMenuOpen" @click.stop>
              <div class="dropdown-hd">
                <img
                  :src="userAvatar"
                  alt=""
                  class="dropdown-avatar"
                  @error="(e) => (e.target.src = '/images/default-avatar.png')"
                />
                <div>
                  <p class="dropdown-name">{{ userName }}</p>
                  <p class="dropdown-role">尊享会员</p>
                </div>
              </div>
              <div class="dropdown-divider"></div>
              <router-link
                to="/user/profile"
                class="dropdown-item"
                @click="userMenuOpen = false"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                  <circle cx="12" cy="7" r="4" />
                </svg>
                个人中心
              </router-link>
              <router-link
                to="/user/orders"
                class="dropdown-item"
                @click="userMenuOpen = false"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <rect x="1" y="3" width="15" height="13" rx="2" />
                  <polygon points="23 7 16 12 23 17 23 7" />
                </svg>
                我的订单
              </router-link>
              <router-link
                to="/user/addresses"
                class="dropdown-item"
                @click="userMenuOpen = false"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                  <circle cx="12" cy="10" r="3" />
                </svg>
                收货地址
              </router-link>
              <div class="dropdown-divider" v-if="isAdmin"></div>
              <router-link
                v-if="isAdmin"
                to="/admin"
                class="dropdown-item admin-entry"
                @click="userMenuOpen = false"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <rect x="2" y="3" width="20" height="14" rx="2" />
                  <line x1="8" y1="21" x2="16" y2="21" />
                  <line x1="12" y1="17" x2="12" y2="21" />
                </svg>
                后台管理
              </router-link>
              <div class="dropdown-divider"></div>
              <button class="dropdown-item logout" @click="handleLogout">
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                  <polyline points="16 17 21 12 16 7" />
                  <line x1="21" y1="12" x2="9" y2="12" />
                </svg>
                退出
              </button>
            </div>
          </Transition>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import { useRouter, useRoute } from "vue-router";
import { useCartStore } from "@/stores/cart.js";
import { useSystemStore } from "@/stores/system.js";
import { useUserStore } from "@/stores/user.js";
import { useLogout } from "@/composables/useLogout.js";
import { useRequireLogin } from "@/composables/useRequireLogin.js";
import { getFurnitureTypeList } from "@/api/furniture.js";
import { imgUrl } from "@/utils/img.js";
import NotificationBell from "@/components/common/NotificationBell.vue";

const router = useRouter();
const route = useRoute();
const cartStore = useCartStore();
const sys = useSystemStore();
const userStore = useUserStore();
const { logout } = useLogout();
const { requireLogin } = useRequireLogin();

const scrolled = ref(false);
const searchOpen = ref(false);
const searchText = ref("");
const searchInput = ref(null);
const userMenuOpen = ref(false);
const userMenuRef = ref(null);

const userName = ref("用户");
const userAvatar = ref("/images/default-avatar.png");
const categories = ref([]);

const isAdmin = computed(() => {
  try {
    const info = JSON.parse(localStorage.getItem("userInfo") || "{}");
    return info.isAdmin === 1;
  } catch {
    return false;
  }
});

// 是否已登录（用 userStore 的响应式 token，退出登录时导航栏立即切换）
const isLoggedIn = computed(() => userStore.isLoggedIn);

// 登录/注册后跳回当前页
const redirectQuery = computed(() => {
  const path = route.fullPath;
  return path && path !== "/" ? { redirect: path } : {};
});

// 收藏入口：未登录引导登录
const handleFavoritesClick = () => {
  if (isLoggedIn.value) {
    router.push("/user/favorites");
  } else {
    requireLogin("收藏功能需要登录");
  }
};

const isImgPath = (str) =>
  str && (str.startsWith("/") || str.startsWith("http"));

const handleLogout = () => {
  userMenuOpen.value = false;
  logout();
};

// Scroll detection
const onScroll = () => {
  scrolled.value = window.scrollY > 10;
};

// User info
const loadUser = () => {
  try {
    const info = JSON.parse(localStorage.getItem("userInfo") || "{}");
    userName.value = info.userName || "用户";
    userAvatar.value = imgUrl(info.icon, "/images/default-avatar.png");
  } catch {
    /* ignore */
  }
};

// Categories
const loadCategories = async () => {
  try {
    const res = await getFurnitureTypeList();
    if (res.success && Array.isArray(res.data))
      categories.value = res.data.slice(0, 8);
  } catch {
    /* ignore */
  }
};

// Search
const toggleSearch = () => {
  searchOpen.value = !searchOpen.value;
  if (searchOpen.value) nextTick(() => searchInput.value?.focus());
};
const doSearch = () => {
  const q = searchText.value.trim();
  if (q) router.push({ path: "/type/0", query: { keyword: q } });
  searchOpen.value = false;
  searchText.value = "";
};

// Route change close
watch(
  () => route.path,
  () => {
    userMenuOpen.value = false;
    searchOpen.value = false;
    // 登录态可能变化（如退出登录跳转），刷新头像/昵称
    loadUser();
  },
);
const closeUserMenu = (e) => {
  if (userMenuRef.value && !userMenuRef.value.contains(e.target))
    userMenuOpen.value = false;
};
const toggleUserMenu = () => {
  userMenuOpen.value = !userMenuOpen.value;
};

// Route change close

onMounted(() => {
  window.addEventListener("scroll", onScroll, { passive: true });
  document.addEventListener("click", closeUserMenu);
  sys.load();
  loadUser();
  loadCategories();
});
onBeforeUnmount(() => {
  window.removeEventListener("scroll", onScroll);
  document.removeEventListener("click", closeUserMenu);
});
</script>

<style scoped lang="scss">
@import "@/styles/views/app-header.scss";
</style>
