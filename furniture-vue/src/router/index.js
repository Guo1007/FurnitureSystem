import { createRouter, createWebHistory } from "vue-router";

const router = createRouter({
  history: createWebHistory(),
  // 新跳转回到顶部；前进/后退恢复浏览器记忆的滚动位置
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    return { top: 0 };
  },
  routes: [
    // Standalone pages (no layout)
    {
      path: "/login",
      name: "Login",
      component: () => import("@/views/auth/LoginView.vue"),
      meta: { public: true },
    },
    {
      path: "/register",
      name: "Register",
      component: () => import("@/views/auth/RegisterView.vue"),
      meta: { public: true },
    },
    {
      path: "/forgot-password",
      name: "ForgotPassword",
      component: () => import("@/views/auth/ForgotPasswordView.vue"),
      meta: { public: true },
    },

    // Main layout — all front-facing pages
    {
      path: "/",
      component: () => import("@/layouts/DefaultLayout.vue"),
      children: [
        {
          path: "",
          name: "Home",
          component: () => import("@/views/home/HomeView.vue"),
          meta: { public: true },
        },
        {
          path: "type/:id",
          name: "TypeDetail",
          component: () => import("@/views/furniture/TypeDetailView.vue"),
          meta: { public: true },
        },
        {
          path: "furniture/detail/:id",
          name: "FurnitureDetail",
          component: () => import("@/views/furniture/FurnitureDetailView.vue"),
          meta: { public: true },
        },
        {
          path: "cart",
          name: "Cart",
          component: () => import("@/views/cart/CartView.vue"),
          meta: { public: true },
        },
        {
          path: "about",
          name: "About",
          component: () => import("@/views/us/AboutView.vue"),
          meta: { public: true },
        },
        {
          path: "coupons",
          name: "Coupons",
          component: () => import("@/views/coupon/CouponCenter.vue"),
          meta: { requiresAuth: true },
        },
        {
          path: "ai-chat",
          name: "AiChat",
          component: () => import("@/views/ai/AiChatPage.vue"),
          meta: { requiresAuth: true },
        },
        {
          path: "user",
          children: [
            {
              path: "profile",
              name: "Profile",
              component: () => import("@/views/user/ProfileView.vue"),
              meta: { requiresAuth: true },
            },
            {
              path: "orders",
              name: "UserOrders",
              component: () => import("@/views/user/UserOrdersView.vue"),
              meta: { requiresAuth: true },
            },
            {
              path: "favorites",
              name: "UserFavorites",
              component: () => import("@/views/user/UserFavoritesView.vue"),
              meta: { requiresAuth: true },
            },
            {
              path: "coupons",
              name: "MyCoupons",
              component: () => import("@/views/coupon/MyCoupons.vue"),
              meta: { requiresAuth: true },
            },
            {
              path: "addresses",
              name: "UserAddresses",
              component: () => import("@/views/user/AddressView.vue"),
              meta: { requiresAuth: true },
            },
          ],
        },
        {
          path: "order/pay/:id",
          name: "OrderPay",
          component: () => import("@/views/order/OrderPayView.vue"),
          meta: { requiresAuth: true },
        },
        {
          path: "notification",
          name: "Notification",
          component: () => import("@/views/user/NotificationView.vue"),
          meta: { requiresAuth: true },
        },
      ],
    },

    // Admin
    {
      path: "/admin",
      component: () => import("@/layouts/admin/AdminLayout.vue"),
      meta: { requiresAdmin: true },
      children: [
        { path: "", redirect: "/admin/dashboard" },
        {
          path: "dashboard",
          component: () => import("@/views/admin/AdminDashboard.vue"),
        },
        {
          path: "users",
          component: () => import("@/views/admin/UserManage.vue"),
        },
        {
          path: "furniture",
          component: () => import("@/views/admin/FurnitureManage.vue"),
        },
        {
          path: "orders",
          component: () => import("@/views/admin/OrderManage.vue"),
        },
        {
          path: "furniture_type",
          component: () => import("@/views/admin/FurnitureTypeManage.vue"),
        },
        {
          path: "coupons",
          component: () => import("@/views/admin/CouponManage.vue"),
        },
        {
          path: "notification",
          component: () => import("@/views/admin/NotificationManage.vue"),
        },
        {
          path: "comments",
          component: () => import("@/views/admin/CommentManage.vue"),
        },
        {
          path: "comment-appends",
          component: () => import("@/views/admin/CommentAppendManage.vue"),
        },
        {
          path: "review-comments",
          component: () => import("@/views/admin/ReviewCommentManage.vue"),
        },
        {
          path: "after-sale",
          component: () => import("@/views/admin/AfterSaleManage.vue"),
        },
        {
          path: "site-content",
          component: () => import("@/views/admin/SiteContentManage.vue"),
        },
        {
          path: "notify-setting",
          component: () => import("@/views/admin/NotifySettingManage.vue"),
        },
        {
          path: "operation-logs",
          component: () => import("@/views/admin/OperationLogManage.vue"),
        },
        {
          path: "nickname-review",
          component: () => import("@/views/admin/NicknameReviewManage.vue"),
        },
        {
          path: "icon-review",
          component: () => import("@/views/admin/IconReviewManage.vue"),
        },
      ],
    },

    // 404
    {
      path: "/:pathMatch(.*)*",
      name: "NotFound",
      component: () => import("@/views/error/NotFoundView.vue"),
      meta: { public: true },
    },
  ],
});

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem("token");
  // JWT 预检：exp 过期则直接清 token，不等 API 401
  // 非标准 JWT（无 exp）回退到原来的 !!token 判断
  let isLoggedIn = false;
  if (token) {
    try {
      const payload = JSON.parse(atob(token.split(".")[1]));
      if (payload.exp) {
        isLoggedIn = payload.exp * 1000 > Date.now();
      } else {
        isLoggedIn = true; // 无 exp 字段，按有效处理
      }
    } catch {
      isLoggedIn = true; // 非 JWT 格式（如 UUID token），按有效处理
    }
    if (!isLoggedIn) {
      localStorage.removeItem("token");
      localStorage.removeItem("userInfo");
    }
  }
  let userRole = null;
  const userInfo = localStorage.getItem("userInfo");
  if (userInfo) {
    try {
      userRole = JSON.parse(userInfo).isAdmin;
    } catch (e) {}
  }
  if (to.meta.requiresAuth && !isLoggedIn) {
    next("/login");
  } else if (to.meta.requiresAdmin && userRole !== 1) {
    next("/");
  } else if (to.path === "/login" && isLoggedIn) {
    next("/");
  } else {
    next();
  }
});

export default router;
