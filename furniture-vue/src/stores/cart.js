// cart.js - 修改后的完整代码

import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";

export const useCartStore = defineStore("cart", () => {
  const items = ref([]);
  const isOpen = ref(false);
  const currentUserId = ref(null);

  const totalCount = computed(() => {
    return items.value.reduce((sum, item) => sum + item.quantity, 0);
  });

  const totalPrice = computed(() => {
    return items.value
      .reduce((sum, item) => {
        return sum + parseFloat(item.price) * item.quantity;
      }, 0)
      .toFixed(2);
  });

  const isEmpty = computed(() => items.value.length === 0);

  const getUserId = () => {
    const userInfo = localStorage.getItem("userInfo");
    if (userInfo) {
      try {
        return JSON.parse(userInfo).id;
      } catch (e) {}
    }
    return null;
  };

  const getStorageKey = (userId) => {
    return userId ? `cart_${userId}` : "cart_guest";
  };

  const saveToStorage = () => {
    const userId = currentUserId.value || getUserId();
    localStorage.setItem(getStorageKey(userId), JSON.stringify(items.value));
  };

  const loadFromStorage = () => {
    const userId = getUserId();
    currentUserId.value = userId;

    const saved = localStorage.getItem(getStorageKey(userId));
    if (!saved) {
      items.value = [];
      return;
    }
    // localStorage 里的购物车可能被其它标签页/旧版本写坏，裸 JSON.parse 抛异常会直接白屏。
    // 解析失败时清掉脏数据并以空购物车启动，保证页面可用。
    try {
      const parsed = JSON.parse(saved);
      items.value = Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      console.error("购物车数据损坏，已重置:", e);
      localStorage.removeItem(getStorageKey(userId));
      items.value = [];
    }
  };

  loadFromStorage();

  const reloadCart = () => {
    loadFromStorage();
  };

  // 添加商品（支持SKU）
  const addItem = (furniture, quantity = 1, skuInfo = null) => {
    const currentId = getUserId();
    if (currentUserId.value !== currentId) {
      reloadCart();
    }

    // 用 furnitureId + skuId 组合作为唯一标识
    const cartItemId = skuInfo
      ? `${furniture.id}_${skuInfo.skuId}`
      : `${furniture.id}`;
    const existingItem = items.value.find(
      (item) => item.cartItemId === cartItemId,
    );

    const maxStock = skuInfo ? skuInfo.stock : furniture.stock;

    if (existingItem) {
      const newQuantity = existingItem.quantity + quantity;
      if (newQuantity > maxStock) {
        ElMessage.warning("库存不足，无法添加更多");
        return false;
      }
      existingItem.quantity = newQuantity;
      ElMessage.success(`已将 ${furniture.fName} 数量增加到 ${newQuantity}`);
    } else {
      items.value.push({
        cartItemId: cartItemId,
        id: furniture.id,
        skuId: skuInfo ? skuInfo.skuId : null,
        specText: skuInfo ? skuInfo.specText : "",
        fName: furniture.fName,
        // 商品分类ID：分类券需据此核算该分类小计（见 CouponPickerDialog 的 subTotals）
        typeId: furniture.typeId ?? furniture.type_id ?? null,
        price: skuInfo ? skuInfo.price : furniture.price,
        fIcon: skuInfo && skuInfo.skuImage ? skuInfo.skuImage : furniture.fIcon,
        stock: maxStock,
        quantity: quantity,
      });
      const specSuffix =
        skuInfo && skuInfo.specText ? `（${skuInfo.specText}）` : "";
      ElMessage.success(`已将 ${furniture.fName}${specSuffix} 加入购物车`);
    }

    saveToStorage();
    return true;
  };

  // 增加数量
  const increaseQuantity = (cartItemId) => {
    const item = items.value.find((i) => i.cartItemId === cartItemId);
    if (item) {
      if (item.quantity >= item.stock) {
        ElMessage.warning("已达到库存上限");
        return;
      }
      item.quantity++;
      saveToStorage();
    }
  };

  // 减少数量
  const decreaseQuantity = async (cartItemId) => {
    const item = items.value.find((i) => i.cartItemId === cartItemId);
    if (!item) return;

    if (item.quantity <= 1) {
      try {
        await ElMessageBox.confirm(`确定要删除 ${item.fName} 吗？`, "提示", {
          confirmButtonText: "删除",
          cancelButtonText: "取消",
          type: "warning",
        });
        removeItem(cartItemId);
        ElMessage.success("已删除该商品");
      } catch {
        // 用户取消
      }
    } else {
      item.quantity--;
      saveToStorage();
    }
  };

  // 删除商品
  const removeItem = (cartItemId) => {
    const index = items.value.findIndex((i) => i.cartItemId === cartItemId);
    if (index > -1) {
      items.value.splice(index, 1);
      saveToStorage();
    }
  };

  // 清空购物车
  const clearCart = async () => {
    if (items.value.length === 0) return;

    try {
      await ElMessageBox.confirm("确定要清空购物车吗？", "提示", {
        confirmButtonText: "清空",
        cancelButtonText: "取消",
        type: "warning",
      });
      items.value = [];
      saveToStorage();
      ElMessage.success("购物车已清空");
    } catch {
      // 用户取消
    }
  };

  // 结算选中的 cartItemId 集合（购物车页勾选后传入抽屉，抽屉按选中项下单；为空则全量结算）
  const checkoutIds = ref([]);

  // 获取购物车数据（用于创建订单）；传入 ids 时仅返回选中的商品
  const getCartData = (ids = null) => {
    const list =
      ids && ids.length
        ? items.value.filter((i) => ids.includes(i.cartItemId))
        : items.value;
    return list.map((item) => ({
      furnitureId: item.id,
      skuId: item.skuId || null,
      quantity: item.quantity,
    }));
  };

  // 结算后清空（传入 ids 时仅清空选中的商品，保留未勾选商品）
  const checkout = (ids = null) => {
    const data = getCartData(ids);
    if (ids && ids.length) {
      items.value = items.value.filter((i) => !ids.includes(i.cartItemId));
    } else {
      items.value = [];
    }
    saveToStorage();
    return data;
  };

  // 用户退出时清空当前状态（不删除存储的数据）
  const clearState = () => {
    items.value = [];
    currentUserId.value = null;
  };

  const toggleCart = () => {
    isOpen.value = !isOpen.value;
  };

  const openCart = () => {
    isOpen.value = true;
  };

  const closeCart = () => {
    isOpen.value = false;
  };

  return {
    items,
    isOpen,
    totalCount,
    totalPrice,
    isEmpty,
    currentUserId,
    checkoutIds,
    addItem,
    increaseQuantity,
    decreaseQuantity,
    removeItem,
    clearCart,
    getCartData,
    checkout,
    reloadCart,
    clearState,
    toggleCart,
    openCart,
    closeCart,
  };
});
