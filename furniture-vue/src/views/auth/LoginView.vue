<template>
  <div class="login-container" @mousemove="onMouseMove">
    <div class="left-panel" :class="{ 'slide-in-left': isLoaded }">
      <div class="decor-grid"></div>
      <div class="brand">
        <div class="brand-icon">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect
              width="28"
              height="28"
              rx="7"
              fill="white"
              fill-opacity="0.15"
            />
            <rect
              x="4"
              y="5"
              width="20"
              height="8"
              rx="2"
              fill="white"
              fill-opacity="0.85"
            />
            <rect
              x="3"
              y="13"
              width="22"
              height="5"
              rx="2.5"
              fill="white"
              fill-opacity="0.95"
            />
            <rect
              x="6"
              y="18"
              width="2.5"
              height="5"
              rx="1.25"
              fill="#D98A4A"
              fill-opacity="0.8"
            />
            <rect
              x="19.5"
              y="18"
              width="2.5"
              height="5"
              rx="1.25"
              fill="#D98A4A"
              fill-opacity="0.8"
            />
          </svg>
        </div>
        <div class="brand-text">
          <span class="brand-name">家具商城</span>
          <span class="brand-slogan">品质家居 · 精选好物</span>
        </div>
      </div>

      <div
        class="flex-1 flex items-end justify-center"
        style="position: relative; z-index: 20"
      >
        <div style="width: 550px; height: 500px; position: relative">
          <AnimatedCharacters
            :mouseX="mouseX"
            :mouseY="mouseY"
            :isTyping="isTyping"
            :showPassword="showPasswordVisible"
          />
        </div>
      </div>
    </div>

    <div class="right-panel" :class="{ 'slide-in-right': isLoaded }">
      <div class="auth-box">
        <router-link to="/" class="back-home-link">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
          返回首页
        </router-link>
        <div class="auth-header">
          <h1>登录</h1>
        </div>
        <div class="auth-tabs">
          <button
            :class="['tab-btn', { active: loginType === 'code' }]"
            @click="loginType = 'code'"
          >
            验证码登录
          </button>
          <button
            :class="['tab-btn', { active: loginType === 'password' }]"
            @click="loginType = 'password'"
          >
            密码登录
          </button>
        </div>

        <!-- 表单 -->
        <form class="auth-form" @submit.prevent="handleLogin">
          <!-- 账号 -->
          <div class="form-group">
            <label>账号</label>
            <div class="input-wrapper">
              <svg
                class="input-icon"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
              </svg>
              <input
                v-model="form.account"
                type="text"
                :placeholder="loginType === 'code' ? '请输入邮箱' : '请输入邮箱或手机号'"
                @focus="onInputFocus"
                @blur="onInputBlur"
              />
            </div>
            <span
              v-if="loginType === 'code'"
              class="form-tip"
              style="font-size: 12px; color: var(--color-text-tertiary)"
              >验证码登录仅支持邮箱账号</span
            >
            <span class="error-msg" v-if="errors.account">{{
              errors.account
            }}</span>
          </div>

          <!-- 验证码登录 -->
          <template v-if="loginType === 'code'">
            <div class="form-group">
              <label>验证码</label>
              <div class="input-wrapper code-wrapper">
                <svg
                  class="input-icon"
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <rect
                    x="3"
                    y="11"
                    width="18"
                    height="11"
                    rx="2"
                    ry="2"
                  ></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
                <input
                  v-model="form.code"
                  type="text"
                  placeholder="输入验证码"
                  maxlength="6"
                  @focus="onInputFocus"
                  @blur="onInputBlur"
                />
                <button
                  type="button"
                  class="code-btn"
                  :disabled="codeCountdown > 0 || !form.account"
                  @click="sendVerifyCode"
                >
                  {{ codeCountdown > 0 ? `${codeCountdown}s` : "获取验证码" }}
                </button>
              </div>
            </div>
          </template>

          <!-- 密码登录 -->
          <template v-else>
            <div class="form-group">
              <label>密码</label>
              <div class="input-wrapper">
                <svg
                  class="input-icon"
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <rect
                    x="3"
                    y="11"
                    width="18"
                    height="11"
                    rx="2"
                    ry="2"
                  ></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
                <input
                  v-model="form.password"
                  :type="showPasswordVisible ? 'text' : 'password'"
                  placeholder="输入您的密码"
                  @focus="onInputFocus"
                  @blur="onInputBlur"
                />
                <button
                  type="button"
                  class="toggle-password"
                  @click="togglePassword"
                >
                  <template v-if="!showPasswordVisible">
                    <svg
                      width="18"
                      height="18"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <path
                        d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"
                      ></path>
                      <circle cx="12" cy="12" r="3"></circle>
                    </svg>
                  </template>
                  <template v-else>
                    <svg
                      width="18"
                      height="18"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <path
                        d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"
                      ></path>
                      <line x1="1" y1="1" x2="23" y2="23"></line>
                    </svg>
                  </template>
                </button>
              </div>
              <div class="form-actions">
                <router-link to="/forgot-password" class="link-text"
                  >忘记密码？</router-link
                >
                <router-link to="/register" class="link-text"
                  >没有账号？立即注册</router-link
                >
              </div>
            </div>
          </template>

          <!-- 提交按钮 -->
          <button type="submit" class="submit-btn" :disabled="loading">
            {{ loading ? "登录中..." : "登录" }}
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { getUserInfo, login, sendCode } from "@/api/user.js";
import { validateEmail, validatePhone } from "@/utils/validators.js";
import { logger } from "@/utils/logger.js";
import { useCartStore } from "@/stores/cart.js";
import { useUserStore } from "@/stores/user.js";
import AnimatedCharacters from "@/components/auth/AnimatedCharacters.vue";

const router = useRouter();
const route = useRoute();

// ========== 表单状态 ==========
const loginType = ref("code");
const form = ref({ account: "", code: "", password: "" });
const errors = ref({ account: "" });
const loading = ref(false);
const codeCountdown = ref(0);
const countdownTimer = ref(null);

// ========== 动画状态 ==========
const mouseX = ref(0);
const mouseY = ref(0);
const isTyping = ref(false);
const showPasswordVisible = ref(false);
const isLoaded = ref(false);

// ========== 载入动画 ==========
onMounted(() => {
  // 延迟一帧触发入场动画，确保DOM已渲染
  requestAnimationFrame(() => {
    isLoaded.value = true;
  });
});

// ========== 鼠标监听 ==========
function onMouseMove(e) {
  mouseX.value = e.clientX;
  mouseY.value = e.clientY;
}

// ========== 输入框聚焦状态 ==========
function onInputFocus() {
  isTyping.value = true;
}

function onInputBlur() {
  isTyping.value = false;
}

// ========== 密码可见切换 ==========
function togglePassword() {
  showPasswordVisible.value = !showPasswordVisible.value;
}

// ========== 表单验证 ==========
function isEmail(val) {
  return val && val.includes("@");
}

function validateAccount() {
  const val = form.value.account;
  if (!val) {
    errors.value.account = "请输入邮箱或手机号";
    return false;
  }
  if (isEmail(val)) {
    const err = validateEmail(val);
    errors.value.account = err;
    return !err;
  } else {
    const err = validatePhone(val);
    errors.value.account = err;
    return !err;
  }
}

// ========== 发送验证码 ==========
async function sendVerifyCode() {
  if (!validateAccount()) {
    ElMessage.warning(errors.value.account || "请输入正确的邮箱或手机号");
    return;
  }
  try {
    const res = await sendCode({ account: form.value.account });
    if (res.success) {
      startCountdown();
      const tip = isEmail(form.value.account)
        ? "验证码已发送至邮箱，请注意查收"
        : "验证码已发送，请注意查收";
      ElMessage.success(tip);
    } else {
      ElMessage.error(res.msg || "发送失败");
    }
  } catch (error) {
    logger.error("发送验证码失败:", error);
  }
}

// ========== 倒计时 ==========
function startCountdown() {
  codeCountdown.value = 60;
  countdownTimer.value = setInterval(() => {
    codeCountdown.value--;
    if (codeCountdown.value <= 0) {
      clearInterval(countdownTimer.value);
    }
  }, 1000);
}

// ========== 登录处理 ==========
async function handleLogin() {
  if (!validateAccount()) {
    ElMessage.warning(errors.value.account || "请输入正确的邮箱或手机号");
    return;
  }
  if (loginType.value === "code" && !form.value.code) {
    ElMessage.warning("请输入验证码");
    return;
  }
  if (loginType.value === "password" && !form.value.password) {
    ElMessage.warning("请输入密码");
    return;
  }

  loading.value = true;
  try {
    const loginData = {
      account: form.value.account,
      code: loginType.value === "code" ? form.value.code : undefined,
      passWord:
        loginType.value === "password" ? form.value.password : undefined,
    };
    const res = await login(loginData);
    if (res.success) {
      const token = res.data;
      const userStore = useUserStore();
      userStore.setToken(token);
      ElMessage.success("登录成功！正在获取用户信息...");
      try {
        const userRes = await getUserInfo();
        if (userRes.success) {
          const userInfo = userRes.data;
          userStore.setUserInfo(userInfo);
          localStorage.setItem("userName", userInfo.userName || "用户");
          localStorage.setItem(
            "userIcon",
            userInfo.icon || "/images/default-avatar.png",
          );
          const cartStore = useCartStore();
          cartStore.reloadCart();
          ElMessage.success(`欢迎回来，${userInfo.userName || "用户"}！`);
        } else {
          ElMessage.warning("获取用户信息失败，但登录成功");
        }
      } catch (userError) {
        ElMessage.warning("获取用户信息失败，但登录成功");
      }
      setTimeout(() => {
        // 登录成功后跳回来源页（游客被引导登录时），否则回首页
        const redirect = route.query.redirect;
        router.push(typeof redirect === "string" && redirect ? redirect : "/");
      }, 800);
    } else {
      ElMessage.error(res.msg || "登录失败");
    }
  } catch (error) {
    logger.error("登录失败:", error);
  } finally {
    loading.value = false;
  }
}

onBeforeUnmount(() => {
  if (countdownTimer.value) {
    clearInterval(countdownTimer.value);
  }
});
</script>

<style scoped lang="scss">
@import "@/styles/views/login-view.scss";
</style>
