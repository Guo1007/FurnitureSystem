<template>
  <div class="cart-page">
    <div class="cart-container">
      <!-- Breadcrumb -->
      <div class="cart-breadcrumb">
        <button class="breadcrumb-back" @click="goBack" title="返回">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
        </button>
        <router-link to="/">首页</router-link>
        <span>/</span>
        <span class="current">购物车</span>
      </div>

      <h1 class="cart-title">
        我的购物车<span class="cart-count" v-if="!cartStore.isEmpty"
          >（{{ cartStore.totalCount }} 件）</span
        >
      </h1>

      <!-- 领券通栏入口 -->
      <router-link to="/coupons" class="coupon-banner">
        <span class="coupon-banner-icon">🎁</span>
        <span class="coupon-banner-text">领券中心 · 满减券、折扣券、无门槛券等你领</span>
        <span class="coupon-banner-btn">去领券</span>
      </router-link>

      <!-- Empty -->
      <div v-if="cartStore.isEmpty" class="cart-empty-state">
        <span class="empty-icon">🛒</span>
        <h2>购物车是空的</h2>
        <p>去挑选一些心仪的家具吧</p>
        <router-link to="/type/0" class="shop-btn">去逛逛</router-link>
      </div>

      <!-- Cart content -->
      <div v-else class="cart-layout">
        <!-- Main -->
        <div class="cart-main">
          <!-- Select all -->
          <div class="cart-select-all">
            <label class="checkbox-label">
              <input
                type="checkbox"
                :checked="allSelected"
                @change="toggleAll"
              />
              <span class="checkmark"></span>
              <span>全选</span>
            </label>
            <button class="clear-btn" @click="cartStore.clearCart">
              清空购物车
            </button>
          </div>

          <!-- Items -->
          <div class="cart-items">
            <div
              class="cart-item"
              v-for="item in cartStore.items"
              :key="item.cartItemId"
            >
              <label class="checkbox-label">
                <input
                  type="checkbox"
                  v-model="selectedIds"
                  :value="item.cartItemId"
                />
                <span class="checkmark"></span>
              </label>
              <img
                :src="imgUrl(item.fIcon, '/images/default-furniture.png')"
                class="item-img"
                @click="goDetail(item.id)"
                @error="handleImgError"
              />
              <div class="item-info">
                <h4 class="item-name" @click="goDetail(item.id)">
                  {{ item.fName }}
                </h4>
                <p class="item-spec" v-if="item.specText">
                  {{ item.specText }}
                </p>
              </div>
              <div class="item-price">¥{{ formatPrice(item.price) }}</div>
              <div class="item-qty">
                <button
                  class="qty-btn"
                  @click="cartStore.decreaseQuantity(item.cartItemId)"
                >
                  −
                </button>
                <span class="qty-val">{{ item.quantity }}</span>
                <button
                  class="qty-btn"
                  @click="cartStore.increaseQuantity(item.cartItemId)"
                  :disabled="item.quantity >= item.stock"
                >
                  +
                </button>
              </div>
              <div class="item-subtotal">
                ¥{{ formatPrice(item.price * item.quantity) }}
              </div>
              <button
                class="item-remove"
                @click="cartStore.removeItem(item.cartItemId)"
                title="删除"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>
          </div>
        </div>

        <!-- Sidebar -->
        <aside class="cart-sidebar">
          <div class="sidebar-card">
            <h3 class="sidebar-title">订单摘要</h3>
            <div class="summary-row">
              <span>商品数量</span>
              <span>{{ selectedCount }} 件</span>
            </div>

            <!-- 优惠券 -->
            <div class="cart-coupon">
              <button class="coupon-picker" @click="toggleCouponPanel">
                <span v-if="selectedCoupons.length">
                  {{ couponText }}
                  <em class="picker-save">-{{ formatPrice(discountEstimate) }}</em>
                </span>
                <span v-else>{{ availableCoupons.length ? "选择优惠券" : "暂无可用优惠券" }}</span>
                <span class="arrow">▾</span>
              </button>
              <div v-if="showCouponPanel" class="coupon-panel">
                <div
                  class="coupon-opt"
                  :class="{ on: !selectedCoupons.length }"
                  @click="selectCoupon(null)"
                >
                  不使用优惠券
                </div>
                <div
                  v-for="c in availableCoupons"
                  :key="c.userCouponId"
                  class="coupon-opt"
                  :class="{ on: isCouponOn(c) }"
                  @click="selectCoupon(c)"
                >
                  <div class="opt-info">
                    <b>{{ c.amountText }}</b>
                    <span>{{ c.name }}</span>
                    <span v-if="Number(c.minThreshold) > 0" class="opt-threshold"
                      >满{{ c.minThreshold }}可用</span
                    >
                    <span v-if="isStackable(c)" class="opt-stackable">可叠加</span>
                    <span v-else class="opt-exclusive">不可叠加</span>
                  </div>
                  <span v-if="isCouponOn(c)" class="opt-check">✓</span>
                  <div class="opt-discount">-{{ formatPrice(estimateCoupon(c)) }}</div>
                </div>
                <div v-if="!availableCoupons.length" class="coupon-none">
                  当前选购商品暂无可用优惠券
                </div>
              </div>
            </div>

            <div class="summary-row total">
              <span>合计</span>
              <span class="total-price">
                <template v-if="discountEstimate > 0">
                  <span class="og-price">¥{{ formatPrice(selectedTotalNum) }}</span>
                  -{{ formatPrice(discountEstimate) }}
                </template>
                ¥{{ formatPrice(selectedTotalNum - discountEstimate) }}
              </span>
            </div>
            <button
              class="checkout-btn"
              :disabled="selectedIds.length === 0 || checkoutLoading"
              @click="goCheckout"
            >
              {{ checkoutLoading ? "提交中..." : "去结算" }}
            </button>
            <router-link to="/type/0" class="continue-link"
              >继续选购</router-link
            >
          </div>

          <!-- Address preview (if available) -->
          <div class="sidebar-card address-card" v-if="defaultAddress">
            <h3 class="sidebar-title">默认收货地址</h3>
            <p class="addr-name">
              {{ defaultAddress.consignee }}
              <span class="addr-phone">{{ defaultAddress.phone }}</span>
            </p>
            <p class="addr-detail">{{ defaultAddress.address }}</p>
            <router-link to="/user/addresses" class="addr-change"
              >修改地址</router-link
            >
          </div>
        </aside>
      </div>

      <!-- Recently viewed -->
      <div class="recent-section" v-if="recentProducts.length > 0">
        <h3 class="recent-title">最近浏览</h3>
        <div class="recent-scroll">
          <ProductCard v-for="p in recentProducts" :key="p.id" :product="p" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useCartStore } from "@/stores/cart.js";
import { getAddressList } from "@/api/address.js";
import { getFurnitureByTypeId } from "@/api/furniture.js";
import { getMyCoupons } from "@/api/coupon.js";
import { createOrder } from "@/api/order.js";
import { imgUrl } from "@/utils/img.js";
import { formatPrice } from "@/utils/format.js";
import { ElMessage } from "element-plus";
import { logger } from "@/utils/logger.js";
import ProductCard from "@/components/product/ProductCard.vue";
import { useBackNavigation } from '@/composables/useBackNavigation.js';
import { useRequireLogin } from "@/composables/useRequireLogin.js";

const router = useRouter();
const cartStore = useCartStore();
const { goBack } = useBackNavigation();
const { requireLogin } = useRequireLogin();
const selectedIds = ref([]);
const defaultAddress = ref(null);
const recentProducts = ref([]);
const checkoutLoading = ref(false);

// ========== 优惠券（支持多选叠加） ==========
const cartCoupons = ref([]);
const selectedCoupons = ref([]);
const showCouponPanel = ref(false);

const selectedTotalNum = computed(
  () =>
    cartStore.items
      .filter((i) => selectedIds.value.includes(i.cartItemId))
      .reduce((s, i) => s + Number(i.price) * i.quantity, 0) || 0,
);

const availableCoupons = computed(() =>
  cartCoupons.value.filter((c) => {
    if (c.status !== 0) return false;
    if (c.expireTime) {
      const t = Array.isArray(c.expireTime)
        ? new Date(c.expireTime[0], c.expireTime[1] - 1, c.expireTime[2])
        : new Date(c.expireTime);
      if (t.getTime() < Date.now()) return false;
    }
    if (Number(c.minThreshold) > selectedTotalNum.value) return false;
    return true;
  }),
);

const isStackable = (c) => c && Number(c.stackable) === 1;
const isCouponOn = (c) =>
  selectedCoupons.value.some((s) => s.userCouponId === c.userCouponId);

const estimateCoupon = (c, total = selectedTotalNum.value) => {
  let d = 0;
  if (c.type === 2 && c.discount) d = total * (1 - Number(c.discount));
  else if (c.amount) d = Number(c.amount);
  if (c.capAmount && d > Number(c.capAmount)) d = Number(c.capAmount);
  d = Math.min(d, total);
  return Math.max(0, Math.round(d * 100) / 100);
};

// 多张券总优惠：按剩余应付金额依次抵扣（与后端一致）
const discountEstimate = computed(() => {
  let payable = selectedTotalNum.value;
  let total = 0;
  for (const c of selectedCoupons.value) {
    const d = estimateCoupon(c, payable);
    total += d;
    payable = Math.max(0, payable - d);
  }
  return Math.round(Math.min(total, selectedTotalNum.value) * 100) / 100;
});

const couponText = computed(() => {
  if (!selectedCoupons.value.length) return "选择优惠券";
  if (selectedCoupons.value.length === 1) return selectedCoupons.value[0].name;
  return `已选 ${selectedCoupons.value.length} 张券`;
});

const loadCoupons = async () => {
  if (!localStorage.getItem("token")) return;
  const res = await getMyCoupons();
  cartCoupons.value = (res.success || res.code === 200) ? res.data || [] : [];
};

const toggleCouponPanel = () => {
  showCouponPanel.value = !showCouponPanel.value;
};
const selectCoupon = (c) => {
  if (!c) {
    selectedCoupons.value = [];
    showCouponPanel.value = false;
    return;
  }
  if (isStackable(c)) {
    // 可叠加券：先剔除已选中的不可叠加券（二者不能共存）
    selectedCoupons.value = selectedCoupons.value.filter((s) => isStackable(s));
    if (isCouponOn(c)) {
      selectedCoupons.value = selectedCoupons.value.filter(
        (s) => s.userCouponId !== c.userCouponId,
      );
    } else {
      selectedCoupons.value.push(c);
    }
  } else {
    // 不可叠加券：只能单独用一张，清空其它
    selectedCoupons.value = isCouponOn(c) ? [] : [c];
  }
  showCouponPanel.value = false;
};

const allSelected = computed({
  get: () =>
    cartStore.items.length > 0 &&
    selectedIds.value.length === cartStore.items.length,
  set: (v) => {
    selectedIds.value = v ? cartStore.items.map((i) => i.cartItemId) : [];
  },
});

const toggleAll = () => {
  allSelected.value = !allSelected.value;
};

const selectedCount = computed(() => {
  return cartStore.items
    .filter((i) => selectedIds.value.includes(i.cartItemId))
    .reduce((s, i) => s + i.quantity, 0);
});

const selectedTotal = computed(() => {
  return formatPrice(
    cartStore.items
      .filter((i) => selectedIds.value.includes(i.cartItemId))
      .reduce((s, i) => s + i.price * i.quantity, 0),
  );
});

const goDetail = (id) => router.push(`/furniture/detail/${id}`);

const goCheckout = async () => {
  // 未登录引导登录
  if (!requireLogin("结算需要登录")) return;
  if (selectedIds.value.length === 0) {
    ElMessage.warning("请选择要结算的商品");
    return;
  }
  if (!defaultAddress.value) {
    ElMessage.warning("请先在个人中心添加收货地址");
    router.push("/user/addresses");
    return;
  }
  checkoutLoading.value = true;
  try {
    const orderData = {
      consignee: defaultAddress.value.consignee,
      phone: defaultAddress.value.phone,
      address: defaultAddress.value.address,
      remark: "",
      userCouponIds:
        selectedCoupons.value.length
          ? selectedCoupons.value.map((c) => c.userCouponId)
          : undefined,
      itemList: cartStore.getCartData([...selectedIds.value]),
    };
    const res = await createOrder(orderData);
    if (res.success || res.code === 200) {
      // 下单成功后移除已结算商品
      cartStore.checkout([...selectedIds.value]);
      ElMessage.success("订单创建成功");
      router.push("/user/orders");
    } else {
      ElMessage.error(res.msg || "订单创建失败");
    }
  } catch (e) {
    logger.error("创建订单失败:", e);
    ElMessage.error("订单创建失败，请稍后重试");
  } finally {
    checkoutLoading.value = false;
  }
};

const handleImgError = (e) => {
  e.target.src = "/images/default-furniture.png";
};

onMounted(async () => {
  selectedIds.value = cartStore.items.map((i) => i.cartItemId);

  // 已登录才加载默认地址（游客浏览购物车不触发需登录接口）
  if (localStorage.getItem("token")) {
    try {
      const res = await getAddressList();
      if ((res.success || res.code === 200) && Array.isArray(res.data)) {
        defaultAddress.value =
          res.data.find((a) => a.isDefault === 1) || res.data[0] || null;
      }
    } catch {
      /* ignore */
    }
    loadCoupons();
  }

  // Recent products
  try {
    const res = await getFurnitureByTypeId({ typeId: 0, current: 1, size: 4 });
    if ((res.success || res.code === 200) && res.data) {
      recentProducts.value = res.data.records || [];
    }
  } catch {
    /* ignore */
  }
});
</script>

<style scoped lang="scss">
@import "@/styles/views/cart-view.scss";

.opt-stackable {
  align-self: flex-start;
  margin-top: 3px;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  color: #389e6d;
  background: #e9f7f0;
}

.opt-exclusive {
  align-self: flex-start;
  margin-top: 3px;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  color: #b4883f;
  background: #fbf4e6;
}

.coupon-opt.on .opt-exclusive {
  color: #c5554a;
  background: #fbeae7;
}

.opt-check {
  font-size: 15px;
  font-weight: 700;
  color: #c5554a;
  flex-shrink: 0;
}
</style>
