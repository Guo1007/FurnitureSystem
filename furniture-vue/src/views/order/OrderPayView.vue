<template>
  <div class="pay-page">
    <!-- 顶部导航 -->
    <div class="top-nav">
      <div class="nav-content">
        <el-button text @click="goBack" class="pay-back-btn">
          <el-icon>
            <ArrowLeft />
          </el-icon>
          返回订单
        </el-button>
        <div class="breadcrumb">订单支付</div>
      </div>
    </div>

    <div class="pay-container">
      <!-- 支付卡片 -->
      <el-card class="pay-card" shadow="never">
        <div class="pay-header">
          <div class="pay-icon">💳</div>
          <h2>订单支付</h2>
          <p class="order-no">订单号：{{ orderId }}</p>
        </div>

        <!-- 订单信息 -->
        <div class="order-info" v-if="orderInfo">
          <div class="info-row">
            <span>收货人</span>
            <span>{{ orderInfo.consignee }}</span>
          </div>
          <div class="info-row">
            <span>联系电话</span>
            <span>{{ orderInfo.phone }}</span>
          </div>
          <div class="info-row">
            <span>收货地址</span>
            <span>{{ orderInfo.address }}</span>
          </div>
        </div>

        <el-divider />

        <!-- 商品列表 -->
        <div class="goods-list" v-if="orderInfo && orderInfo.itemList">
          <h4>商品明细</h4>
          <div
            v-for="item in orderInfo.itemList"
            :key="item.id"
            class="goods-item"
          >
            <span class="name">{{ item.furnitureName }}</span>
            <span class="count">×{{ item.quantity }}</span>
            <span class="price">¥{{ formatPrice(item.itemTotalPrice) }}</span>
          </div>
        </div>

        <el-divider />

        <!-- 金额明细（含优惠券抵扣） -->
        <div class="amount-detail" v-if="Number(orderInfo?.couponDiscount || 0) > 0">
          <div class="info-row">
            <span>商品总额</span>
            <span>¥{{ formatPrice(Number(orderInfo.totalPrice) + Number(orderInfo.couponDiscount)) }}</span>
          </div>
          <div class="info-row discount">
            <span>优惠券抵扣</span>
            <span>-¥{{ formatPrice(orderInfo.couponDiscount) }}</span>
          </div>
          <el-divider />
        </div>

        <!-- 支付金额 -->
        <div class="pay-amount">
          <span>应付金额</span>
          <span class="amount">¥{{ formatPrice(orderInfo?.totalPrice) }}</span>
        </div>

        <!-- 支付倒计时 -->
        <div class="countdown-section" v-if="remainingMs > 0">
          <div
            class="countdown-box"
            :class="{ warning: isWarning, urgent: isUrgent }"
          >
            <span class="countdown-icon">⏱</span>
            <span class="countdown-label">请在</span>
            <span class="countdown-time">{{ countdownText }}</span>
            <span class="countdown-label">内完成支付，超时订单将自动取消</span>
          </div>
        </div>
        <div
          class="countdown-section expired"
          v-else-if="orderInfo?.createTime"
        >
          <span>⏰ 订单已超时，即将返回订单列表...</span>
        </div>

        <!-- 支付方式 -->
        <div class="pay-method">
          <h4>选择支付方式</h4>
          <div class="method-options">
            <div
              class="method-item"
              :class="{ active: payMethod === 'wechat' }"
              @click="payMethod = 'wechat'"
            >
              <span class="icon"></span>
              <span>微信支付</span>
            </div>
            <div
              class="method-item"
              :class="{ active: payMethod === 'alipay' }"
              @click="payMethod = 'alipay'"
            >
              <span class="icon"></span>
              <span>支付宝</span>
            </div>
          </div>
        </div>

        <!-- 支付按钮 -->
        <div class="pay-action">
          <el-button
            type="primary"
            size="large"
            :loading="paying"
            @click="handlePay"
            class="pay-btn"
          >
            确认支付 ¥{{ formatPrice(orderInfo?.totalPrice) }}
          </el-button>
          <el-button text @click="cancelPay" class="cancel-btn">
            取消支付
          </el-button>
        </div>

        <!-- 等待支付结果提示 -->
        <div v-if="waitTipVisible" class="wait-tip">
          <span class="wait-spinner"></span>
          <span>已拉起支付宝收银台，请在新窗口完成付款，本页将自动确认支付结果…</span>
        </div>
      </el-card>

      <!-- 支付成功弹窗 -->
      <el-dialog
        v-model="successDialogVisible"
        title="支付成功"
        width="400px"
        :close-on-click-modal="false"
        :show-close="false"
        center
      >
        <div class="success-content">
          <div class="success-icon">✓</div>
          <p class="success-text">支付成功！</p>
          <p class="success-tip">感谢您的购买，我们将尽快为您发货</p>
        </div>
        <template #footer>
          <div class="success-footer">
            <span class="success-redirect">{{ successRedirect }} 秒后自动跳转订单列表…</span>
            <div>
              <el-button type="primary" @click="goToOrders" size="large">
                查看订单
              </el-button>
              <el-button @click="goHome" size="large">返回首页</el-button>
            </div>
          </div>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { getOrderDetail, prepayOrder } from "@/api/order.js";
import { formatPrice } from "@/utils/format.js";
import { logger } from "@/utils/logger.js";

const PAYMENT_TIMEOUT_MINUTES = 1440; // 24小时，与后端 order.payment-timeout-minutes 保持一致

const route = useRoute();
const router = useRouter();
const orderId = ref(route.params.id);

const orderInfo = ref({});
const payMethod = ref("wechat");
const paying = ref(false);
const successDialogVisible = ref(false);
const remainingMs = ref(0); // 剩余毫秒数
const waitTipVisible = ref(false); // 是否显示"等待支付结果"提示
let countdownTimer = null;
let redirectTimer = null;
let pollTimer = null; // 轮询订单状态定时器
let successRedirectTimer = null; // 支付成功自动跳转定时器
const successRedirect = ref(3); // 支付成功后倒计时秒数

// 计算倒计时截止时间
const deadline = computed(() => {
  if (!orderInfo.value?.createTime) return null;
  const created = new Date(orderInfo.value.createTime.replace(" ", "T"));
  return new Date(created.getTime() + PAYMENT_TIMEOUT_MINUTES * 60 * 1000);
});

// 倒计时显示文本 HH:MM:SS.XX
const countdownText = computed(() => {
  if (remainingMs.value <= 0) return "00:00:00.00";
  const ts = remainingMs.value / 1000;
  const h = Math.floor(ts / 3600);
  const m = Math.floor((ts % 3600) / 60);
  const s = Math.floor(ts % 60);
  const cs = Math.floor((ts % 1) * 100);
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}.${String(cs).padStart(2, "0")}`;
});

// 剩余不足 10 分钟 → 紧急
const isUrgent = computed(
  () => remainingMs.value > 0 && remainingMs.value <= 600_000,
);
// 剩余不足 1 小时 → 提醒
const isWarning = computed(
  () => remainingMs.value > 600_000 && remainingMs.value <= 3_600_000,
);

// 定时更新倒计时（50ms 刷新一次，百分秒看得见跳动）
const tick = () => {
  if (!deadline.value) return;
  const diff = deadline.value.getTime() - Date.now();
  remainingMs.value = Math.max(0, diff);
  if (diff <= 0) {
    clearInterval(countdownTimer);
    countdownTimer = null;
    // 延迟 3 秒后跳转
    redirectTimer = setTimeout(() => {
      ElMessage.warning("订单支付超时，已自动取消");
      router.push("/user/orders");
    }, 3000);
  }
};

// 加载订单信息
const loadOrderInfo = async () => {
  try {
    const res = await getOrderDetail(orderId.value);
    if (res.success || res.code === 200) {
      orderInfo.value = res.data;
      if (res.data.status !== 0) {
        ElMessage.info("该订单已支付或已取消");
        router.push("/user/orders");
        return;
      }
      // 启动倒计时（50ms 刷新，百分秒可见）
      tick();
      countdownTimer = setInterval(tick, 50);
    } else {
      ElMessage.error(res.msg || "获取订单失败");
      router.push("/user/orders");
    }
  } catch (error) {
    logger.error("加载订单失败:", error);
    router.push("/user/orders");
  }
};

const handlePay = async () => {
  paying.value = true;
  // 在点击同步阶段打开新窗口，避免被浏览器弹窗拦截
  const win = window.open("", "_blank");
  try {
    const res = await prepayOrder(orderId.value);
    if (res.success || res.code === 200) {
      if (res.data) {
        // 支付宝返回自动提交的付款表单 HTML，写入新窗口并触发提交
        win.document.write(res.data);
        win.document.close();
        // 拉起支付宝后，本页轮询等待支付结果
        startPolling();
      } else {
        if (win) win.close();
        ElMessage.error("未获取到支付页面，请重试");
      }
    } else {
      if (win) win.close();
      ElMessage.error(res.msg || "支付失败");
    }
  } catch (error) {
    if (win) win.close();
    logger.error("发起支付失败:", error);
    ElMessage.error("发起支付失败");
  } finally {
    paying.value = false;
  }
};

// 停止轮询并隐藏等待提示
const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  waitTipVisible.value = false;
};

// 付款后每 2 秒轮询一次订单状态，付款成功自动弹窗并跳转
const startPolling = () => {
  stopPolling();
  waitTipVisible.value = true;
  pollTimer = setInterval(async () => {
    try {
      const res = await getOrderDetail(orderId.value);
      if (res.success || res.code === 200) {
        const status = res.data?.status;
        if (typeof status === "number" && status >= 1) {
          stopPolling();
          orderInfo.value = res.data;
          successDialogVisible.value = true;
        }
      }
    } catch (error) {
      logger.error("轮询订单状态失败:", error);
    }
  }, 2000);
};

// 支付成功弹窗出现后，启动跳转倒计时
const startSuccessRedirect = () => {
  successRedirect.value = 3;
  successRedirectTimer = setInterval(() => {
    successRedirect.value -= 1;
    if (successRedirect.value <= 0) {
      stopSuccessRedirect();
      goToOrders();
    }
  }, 1000);
};

const stopSuccessRedirect = () => {
  if (successRedirectTimer) {
    clearInterval(successRedirectTimer);
    successRedirectTimer = null;
  }
};

const cancelPay = () => {
  stopPolling();
  ElMessage.info("您已取消支付");
  router.push("/user/orders");
};

const goBack = () => {
  router.back();
};

const goToOrders = () => {
  stopPolling();
  stopSuccessRedirect();
  successDialogVisible.value = false;
  router.push("/user/orders");
};

const goHome = () => {
  stopPolling();
  stopSuccessRedirect();
  successDialogVisible.value = false;
  router.push("/");
};

onMounted(() => {
  loadOrderInfo();
});

// 支付成功弹窗弹出时启动自动跳转倒计时
watch(successDialogVisible, (visible) => {
  if (visible) {
    startSuccessRedirect();
  } else {
    stopSuccessRedirect();
  }
});

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
  if (redirectTimer) {
    clearTimeout(redirectTimer);
    redirectTimer = null;
  }
  stopPolling();
  stopSuccessRedirect();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/payView.scss";
@import "@/styles/views/order-pay-view.scss";

.wait-tip {
  margin-top: 16px;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;

  .wait-spinner {
    width: 14px;
    height: 14px;
    border: 2px solid currentColor;
    border-top-color: transparent;
    border-radius: 50%;
    animation: wait-spin 0.8s linear infinite;
    flex-shrink: 0;
  }
}

@keyframes wait-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.success-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;

  .success-redirect {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}
</style>
