<template>
  <div class="cart-wrapper">
    <!-- 购物车悬浮按钮 -->
    <div
      class="cart-float-btn"
      :style="btnStyle"
      @mousedown="startDrag"
      @touchstart="startDrag"
      @click="handleClick"
      v-show="!cartStore.isOpen"
    >
      <el-badge
        :value="cartStore.totalCount"
        :hidden="cartStore.isEmpty"
        class="cart-badge"
      >
        <div class="cart-icon">🛒</div>
      </el-badge>
      <div class="cart-price" v-if="!cartStore.isEmpty">
        ¥{{ cartStore.totalPrice }}
      </div>
    </div>

    <!-- 购物车抽屉 -->
    <el-drawer
      :model-value="cartStore.isOpen"
      @update:model-value="onDrawerClose"
      title="购物车"
      size="480px"
      :with-header="true"
      class="cart-drawer"
    >
      <div class="cart-content">
        <!-- 领券通栏入口 -->
        <router-link to="/coupons" class="coupon-banner" @click="cartStore.closeCart()">
          <span class="coupon-banner-icon">🎁</span>
          <span class="coupon-banner-text">领券中心 · 满减券、折扣券、无门槛券等你领</span>
          <span class="coupon-banner-btn">去领券</span>
        </router-link>

        <!-- 空状态 -->
        <div v-if="cartStore.isEmpty" class="cart-empty">
          <div class="empty-icon">🛒</div>
          <p>购物车是空的</p>
          <el-button type="primary" @click="goShopping">去逛逛</el-button>
        </div>

        <!-- 商品列表 -->
        <div v-else class="cart-list">
          <div
            v-for="item in cartStore.items"
            :key="item.cartItemId"
            class="cart-item"
          >
            <img
              :src="imgUrl(item.fIcon, '/images/default-furniture.png')"
              class="item-img"
              @click="goToDetail(item.id)"
            />
            <div class="item-info">
              <h4 @click="goToDetail(item.id)">{{ item.fName }}</h4>
              <p class="item-spec" v-if="item.specText">{{ item.specText }}</p>
              <p class="item-price">¥{{ formatPrice(item.price) }}</p>
            </div>
            <div class="item-actions">
              <div class="cart-qty-control">
                <button
                  class="cart-qty-btn"
                  @click="cartStore.decreaseQuantity(item.cartItemId)"
                >
                  -
                </button>
                <span class="cart-qty-value">{{ item.quantity }}</span>
                <button
                  class="cart-qty-btn"
                  @click="cartStore.increaseQuantity(item.cartItemId)"
                  :disabled="item.quantity >= item.stock"
                >
                  +
                </button>
              </div>
              <div class="item-total">
                ¥{{ formatPrice(item.price * item.quantity) }}
              </div>
              <el-button
                type="danger"
                text
                size="small"
                @click="cartStore.removeItem(item.cartItemId)"
                class="delete-btn"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 优惠券 -->
      <div v-if="!cartStore.isEmpty" class="coupon-select">
        <div class="coupon-select-hd">
          <span class="label">优惠券</span>
          <button class="picker" @click="toggleCouponPanel">
            <span v-if="selectedCoupons.length">
              {{ couponText }}
              <em class="picker-save">-{{ formatPrice(discountEstimate) }}</em>
            </span>
            <span v-else>{{ availableCoupons.length ? "选择优惠券" : "暂无可用优惠券" }}</span>
            <span class="arrow">▾</span>
          </button>
        </div>
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
            当前购物车暂无可用优惠券
          </div>
        </div>
      </div>

      <!-- 收货地址 -->
      <div v-if="!cartStore.isEmpty" class="address-section">
        <div class="address-header">
          <span class="address-label">📍 收货地址</span>
        </div>

        <!-- 有地址：显示当前选中 + 切换入口 -->
        <template v-if="addressList.length > 0 && !showAddressForm">
          <div
            class="address-card"
            @click="showAddressPicker = !showAddressPicker"
          >
            <div class="addr-contact">
              <span class="addr-name">{{ selectedAddress.consignee }}</span>
              <span class="addr-phone">{{ selectedAddress.phone }}</span>
            </div>
            <div class="addr-detail">{{ selectedAddress.address }}</div>
            <div class="addr-change">
              <span class="change-link">{{
                showAddressPicker ? "收起" : "切换地址"
              }}</span>
            </div>
          </div>
          <!-- 地址选择列表 -->
          <div v-if="showAddressPicker" class="address-picker">
            <div
              v-for="addr in addressList"
              :key="addr.id"
              class="picker-item"
              :class="{ active: selectedAddress.id === addr.id }"
              @click="selectAddress(addr)"
            >
              <div class="picker-contact">
                <span>{{ addr.consignee }}</span>
                <span class="picker-phone">{{ addr.phone }}</span>
                <el-tag
                  v-if="addr.isDefault === 1"
                  size="small"
                  type="warning"
                  effect="plain"
                  >默认</el-tag
                >
              </div>
              <div class="picker-addr">{{ addr.address }}</div>
            </div>
          </div>
        </template>

        <!-- 无地址：显示添加表单 -->
        <template v-if="addressList.length === 0 || showAddressForm">
          <div class="address-form">
            <el-input
              v-model="addrForm.consignee"
              placeholder="收货人姓名"
              size="small"
              class="form-input"
            />
            <el-input
              v-model="addrForm.phone"
              placeholder="手机号码"
              size="small"
              class="form-input"
            />
            <el-input
              v-model="addrForm.address"
              placeholder="详细地址"
              size="small"
              class="form-input"
            />
            <div class="form-actions">
              <el-button
                v-if="addressList.length > 0"
                size="small"
                @click="showAddressForm = false"
                >取消</el-button
              >
              <el-button
                type="primary"
                size="small"
                :loading="savingAddress"
                @click="submitNewAddress"
              >
                保存并使用
              </el-button>
            </div>
          </div>
        </template>

        <!-- 有地址但未展示表单时，显示"添加新地址"入口 -->
        <div
          v-if="
            addressList.length > 0 && !showAddressForm && !showAddressPicker
          "
          class="add-new-link"
          @click="showAddressForm = true"
        >
          + 使用新地址
        </div>
      </div>

      <!-- 底部结算栏 -->
      <template #footer v-if="!cartStore.isEmpty">
        <div class="cart-footer">
          <div class="footer-info">
            <span class="total-count">共 {{ cartStore.totalCount }} 件</span>
            <span class="total-price">
              <template v-if="discountEstimate > 0">
                <span class="og-price">¥{{ formatPrice(cartStore.totalPrice) }}</span>
                -{{ formatPrice(discountEstimate) }}
              </template>
              合计：¥{{ formatPrice(cartStore.totalPrice - discountEstimate) }}
            </span>
          </div>
          <div class="footer-actions">
            <el-button type="danger" plain @click="cartStore.clearCart"
              >清空</el-button
            >
            <el-button type="primary" size="large" @click="checkout"
              >去结算</el-button
            >
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { useCartStore } from "@/stores/cart.js";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { createOrder } from "@/api/order.js";
import { getAddressList, saveAddress } from "@/api/address.js";
import { getMyCoupons } from "@/api/coupon.js";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { imgUrl } from "@/utils/img.js";
import { formatPrice } from "@/utils/format.js";
import { logger } from "@/utils/logger.js";
import { useRequireLogin } from "@/composables/useRequireLogin.js";

const cartStore = useCartStore();
const router = useRouter();
const { requireLogin } = useRequireLogin();

// ========== 优惠券 ==========
const mineCoupons = ref([]);
const selectedCoupons = ref([]);
const showCouponPanel = ref(false);

const loadCoupons = async () => {
  const res = await getMyCoupons();
  mineCoupons.value = (res.success || res.code === 200) ? res.data || [] : [];
};

// 可用券：未用+未过期+满足门槛
const availableCoupons = computed(() => {
  const total = Number(cartStore.totalPrice || 0);
  return mineCoupons.value.filter((c) => {
    if (c.status !== 0) return false;
    if (c.expireTime) {
      const t = Array.isArray(c.expireTime)
        ? new Date(c.expireTime[0], c.expireTime[1] - 1, c.expireTime[2])
        : new Date(c.expireTime);
      if (t.getTime() < Date.now()) return false;
    }
    if (Number(c.minThreshold) > total) return false;
    return true;
  });
});

const isStackable = (c) => c && Number(c.stackable) === 1;
const isCouponOn = (c) =>
  selectedCoupons.value.some((s) => s.userCouponId === c.userCouponId);

// 预估优惠额（与服务端口径一致，最终以服务端为准）
const estimateCoupon = (c, total = Number(cartStore.totalPrice || 0)) => {
  let d = 0;
  if (c.type === 2 && c.discount) d = total * (1 - Number(c.discount));
  else if (c.amount) d = Number(c.amount);
  if (c.capAmount && d > Number(c.capAmount)) d = Number(c.capAmount);
  d = Math.min(d, total);
  return Math.max(0, Math.round(d * 100) / 100);
};

// 多张券总优惠：按剩余应付金额依次抵扣（与后端一致）
const discountEstimate = computed(() => {
  let payable = Number(cartStore.totalPrice || 0);
  let total = 0;
  for (const c of selectedCoupons.value) {
    const d = estimateCoupon(c, payable);
    total += d;
    payable = Math.max(0, payable - d);
  }
  return Math.round(Math.min(total, Number(cartStore.totalPrice || 0)) * 100) / 100;
});

const couponText = computed(() => {
  if (!selectedCoupons.value.length) return "选择优惠券";
  if (selectedCoupons.value.length === 1) return selectedCoupons.value[0].name;
  return `已选 ${selectedCoupons.value.length} 张券`;
});

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

const btnPosition = ref({ x: 0, y: 0 });
const isDragging = ref(false);
const dragStart = ref({ x: 0, y: 0 });
const btnStart = ref({ x: 0, y: 0 });
const hasMoved = ref(false);

onMounted(() => {
  const saved = localStorage.getItem("cartBtnPosition");
  if (saved) {
    btnPosition.value = JSON.parse(saved);
  } else {
    btnPosition.value = {
      x: window.innerWidth - 100,
      y: window.innerHeight - 150,
    };
  }
});

const btnStyle = computed(() => ({
  left: btnPosition.value.x + "px",
  top: btnPosition.value.y + "px",
  cursor: isDragging.value ? "grabbing" : "grab",
}));

// 开始拖动
const startDrag = (e) => {
  isDragging.value = true;
  hasMoved.value = false;
  const clientX = e.touches ? e.touches[0].clientX : e.clientX;
  const clientY = e.touches ? e.touches[0].clientY : e.clientY;
  dragStart.value = { x: clientX, y: clientY };
  btnStart.value = { ...btnPosition.value };
  document.addEventListener("mousemove", onDrag);
  document.addEventListener("mouseup", stopDrag);
  document.addEventListener("touchmove", onDrag);
  document.addEventListener("touchend", stopDrag);
};

// 拖动中
const onDrag = (e) => {
  if (!isDragging.value) return;

  const clientX = e.touches ? e.touches[0].clientX : e.clientX;
  const clientY = e.touches ? e.touches[0].clientY : e.clientY;
  const dx = Math.abs(clientX - dragStart.value.x);
  const dy = Math.abs(clientY - dragStart.value.y);
  if (dx > 5 || dy > 5) {
    hasMoved.value = true;
  }
  if (hasMoved.value) {
    e.preventDefault();
  }
  let newX = btnStart.value.x + (clientX - dragStart.value.x);
  let newY = btnStart.value.y + (clientY - dragStart.value.y);
  const maxX = window.innerWidth - 80;
  const maxY = window.innerHeight - 80;
  newX = Math.max(10, Math.min(newX, maxX));
  newY = Math.max(10, Math.min(newY, maxY));
  btnPosition.value = { x: newX, y: newY };
};

// 停止拖动
const stopDrag = () => {
  isDragging.value = false;
  localStorage.setItem("cartBtnPosition", JSON.stringify(btnPosition.value));
  cleanupListeners();
};

const cleanupListeners = () => {
  document.removeEventListener("mousemove", onDrag);
  document.removeEventListener("mouseup", stopDrag);
  document.removeEventListener("touchmove", onDrag);
  document.removeEventListener("touchend", stopDrag);
};

onUnmounted(() => {
  cleanupListeners();
});

// 点击处理 - 只有没移动过才打开购物车
const handleClick = () => {
  if (hasMoved.value) {
    hasMoved.value = false;
    return;
  }
  cartStore.isOpen = true;
};

const onDrawerClose = (val) => {
  cartStore.isOpen = val;
};

const goShopping = () => {
  cartStore.closeCart();
  router.push("/");
};

const goToDetail = (id) => {
  cartStore.closeCart();
  router.push(`/furniture/detail/${id}`);
};

// ========== 收货地址 ==========
const addressList = ref([]);
const selectedAddress = ref({});
const showAddressPicker = ref(false);
const showAddressForm = ref(false);
const savingAddress = ref(false);
const addrForm = ref({ consignee: "", phone: "", address: "" });

// 加载地址列表
const loadAddresses = async () => {
  try {
    const res = await getAddressList();
    if ((res.success || res.code === 200) && res.data) {
      addressList.value = res.data;
      if (addressList.value.length > 0) {
        // 优先默认地址，否则第一个
        const addr =
          addressList.value.find((a) => a.isDefault === 1) ||
          addressList.value[0];
        selectedAddress.value = addr;
      }
    }
  } catch (e) {
    logger.error("获取收货地址失败:", e);
  }
};

// 切换地址
const selectAddress = (addr) => {
  selectedAddress.value = addr;
  showAddressPicker.value = false;
};

// 保存新地址并使用
const submitNewAddress = async () => {
  const { consignee, phone, address } = addrForm.value;
  if (!consignee.trim()) return ElMessage.warning("请输入收货人姓名");
  if (!phone.trim()) return ElMessage.warning("请输入手机号码");
  if (!address.trim()) return ElMessage.warning("请输入详细地址");
  if (
    !/^1([38][0-9]|4[579]|5[0-35-9]|6[2567]|7[0-8]|9[0-9])\d{8}$/.test(
      phone.trim(),
    )
  )
    return ElMessage.warning("手机号格式不正确");

  savingAddress.value = true;
  try {
    const res = await saveAddress({
      consignee: consignee.trim(),
      phone: phone.trim(),
      address: address.trim(),
    });
    if (res.success || res.code === 200) {
      // 重新加载地址列表，选中新地址
      await loadAddresses();
      showAddressForm.value = false;
      addrForm.value = { consignee: "", phone: "", address: "" };
      // 如果新增的就是列表中唯一的地址，自动选中
      if (addressList.value.length > 0 && res.data) {
        const newAddr = addressList.value.find((a) => a.id === res.data);
        if (newAddr) selectedAddress.value = newAddr;
      }
    } else {
      ElMessage.error(res.msg || "保存地址失败");
    }
  } catch (e) {
    logger.error("保存地址失败:", e);
  } finally {
    savingAddress.value = false;
  }
};

// 购物车打开时加载地址（仅登录用户，游客浏览购物车不触发需登录接口）
watch(
  () => cartStore.isOpen,
  (open) => {
    if (open) {
      showAddressPicker.value = false;
      showAddressForm.value = false;
      if (localStorage.getItem("token")) {
        loadAddresses();
        loadCoupons();
      }
    } else {
      // 抽屉关闭时清除本次结算的勾选集合，避免残留影响下次结算
      cartStore.checkoutIds = [];
    }
  },
);

const checkout = async () => {
  // 未登录引导登录
  if (!requireLogin("结算需要登录")) return;
  if (cartStore.isEmpty) {
    ElMessage.warning("购物车是空的");
    return;
  }

  // 确保已选中地址
  if (
    !selectedAddress.value?.consignee ||
    !selectedAddress.value?.phone ||
    !selectedAddress.value?.address
  ) {
    ElMessage.warning("请先选择或添加收货地址");
    return;
  }

  try {
    const orderData = {
      consignee: selectedAddress.value.consignee,
      phone: selectedAddress.value.phone,
      address: selectedAddress.value.address,
      remark: "",
      userCouponIds:
        selectedCoupons.value.length
          ? selectedCoupons.value.map((c) => c.userCouponId)
          : undefined,
      // 优先结算购物车页勾选的商品；从顶部图标直接打开抽屉时为空，则结算全部
      itemList: cartStore.getCartData(cartStore.checkoutIds),
    };

    const res = await createOrder(orderData);
    if (res.success || res.code === 200) {
      cartStore.checkout(cartStore.checkoutIds);
      cartStore.checkoutIds = [];
      cartStore.closeCart();
      ElMessage.success("订单创建成功");
      router.push("/user/orders");
    } else {
      ElMessage.error(res.msg || "订单创建失败");
    }
  } catch (error) {
    logger.error("创建订单失败:", error);
  }
};
</script>

<style scoped lang="scss">
@import "@/styles/views/cart.scss";
@import "@/styles/views/cart-drawer.scss";

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
