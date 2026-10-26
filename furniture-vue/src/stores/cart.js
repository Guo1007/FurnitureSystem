import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import router from "@/router";
import {
  getCartList,
  addCartItem,
  updateCartItemQuantity,
  removeCartItem,
  clearCart as clearCartApi,
} from "@/api/cart";

/**
 * 购物车状态。
 * <p>
 * 数据来源为服务端 /cart 接口，不再读写 localStorage —— 换设备/换浏览器后购物车仍在。
 * 服务端返回的是含实时价格与库存的 VO，这里映射成前端既有的字段结构
 * （cartItemId / id / fName …），使上层模板无需跟着改字段名。
 * <p>
 * 注意模块依赖成环：stores/cart → api/cart → api/request → stores/cart。
 * request.js 只在 401 处理函数体内调用 useCartStore()，不在顶层执行，
 * 因此可正常运行；**切勿在 request.js 顶层使用本 store**，否则会拿到 undefined。
 */
export const useCartStore = defineStore("cart", () => {
  const items = ref([]);
  const isOpen = ref(false);
  /** 是否已从服务端取过数据，用于购物车页的空态判定 */
  const loaded = ref(false);
  const loading = ref(false);

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

  /**
   * 服务端 VO → 前端既有结构。
   * 保留这一层是为了让 CartView / CartDrawer 的模板沿用原字段名，减少改造面。
   */
  const toLocalItem = (vo) => ({
    cartItemId: vo.id,
    id: vo.furnitureId,
    skuId: vo.skuId,
    specText: vo.specText || "",
    fName: vo.fName || "",
    typeId: vo.typeId,
    price: vo.price,
    fIcon: vo.fIcon || "",
    stock: vo.stock,
    quantity: vo.quantity,
    available: vo.available !== false,
    unavailableReason: vo.unavailableReason || "",
  });

  const isLoggedIn = () => !!localStorage.getItem("token");

  /**
   * 加购前置检查：未登录直接拦在这里并跳登录，不靠 401 驱动提示。
   * 放在 store 而非各个调用点，是为了避免 8 处重复写同一段逻辑。
   */
  const ensureLoggedIn = () => {
    if (isLoggedIn()) return true;
    ElMessage.warning("请先登录后再加入购物车");
    router.push({
      path: "/login",
      query: { redirect: router.currentRoute.value.fullPath },
    });
    return false;
  };

  /** 拉取购物车；未登录时直接置空，避免游客反复 401 */
  const fetchCart = async () => {
    if (!isLoggedIn()) {
      items.value = [];
      loaded.value = true;
      return;
    }
    loading.value = true;
    try {
      const res = await getCartList();
      if (res && (res.success || res.code === 200)) {
        items.value = (res.data || []).map(toLocalItem);
      }
    } catch {
      // 网络错误已由 request.js 统一提示，这里只保证状态可用
    } finally {
      loading.value = false;
      loaded.value = true;
    }
  };

  /**
   * 加入购物车。同一 SKU 已存在时服务端累加数量。
   *
   * @param furniture 商品对象（需含 id、fName）
   * @param quantity  数量
   * @param skuInfo   规格对象（需含 skuId、specText），无规格传 null
   * @param silent    true 时不弹单条提示，供套装批量加购使用
   * @returns {Promise<boolean>} 是否成功
   */
  const addItem = async (furniture, quantity = 1, skuInfo = null, silent = false) => {
    if (!ensureLoggedIn()) return false;
    if (!furniture || !furniture.id) return false;
    const res = await addCartItem({
      furnitureId: furniture.id,
      skuId: skuInfo && skuInfo.skuId ? skuInfo.skuId : null,
      quantity: quantity || 1,
    });
    if (!res || !(res.success || res.code === 200)) {
      if (!silent) ElMessage.warning((res && res.msg) || "加入购物车失败");
      return false;
    }
    await fetchCart();
    if (!silent) {
      const specSuffix = skuInfo && skuInfo.specText ? `（${skuInfo.specText}）` : "";
      ElMessage.success(`已将 ${furniture.fName}${specSuffix} 加入购物车`);
    }
    return true;
  };

  /** 增加数量；本地乐观更新，避免整表重拉 */
  const increaseQuantity = async (cartItemId) => {
    const item = items.value.find((i) => i.cartItemId === cartItemId);
    if (!item) return;
    if (item.quantity >= item.stock) {
      ElMessage.warning("已达到库存上限");
      return;
    }
    const res = await updateCartItemQuantity(cartItemId, item.quantity + 1);
    if (res && (res.success || res.code === 200)) {
      item.quantity += 1;
    } else {
      ElMessage.warning((res && res.msg) || "修改数量失败");
    }
  };

  /** 减少数量；减到 1 再减则确认删除 */
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
        if (await removeItem(cartItemId)) {
          ElMessage.success("已删除该商品");
        }
      } catch {
        // 用户取消
      }
      return;
    }
    const res = await updateCartItemQuantity(cartItemId, item.quantity - 1);
    if (res && (res.success || res.code === 200)) {
      item.quantity -= 1;
    } else {
      ElMessage.warning((res && res.msg) || "修改数量失败");
    }
  };

  /** 删除单项 */
  const removeItem = async (cartItemId) => {
    const res = await removeCartItem(cartItemId);
    if (res && (res.success || res.code === 200)) {
      items.value = items.value.filter((i) => i.cartItemId !== cartItemId);
      return true;
    }
    ElMessage.warning((res && res.msg) || "删除失败");
    return false;
  };

  /** 清空购物车 */
  const clearCart = async () => {
    if (items.value.length === 0) return;
    try {
      await ElMessageBox.confirm("确定要清空购物车吗？", "提示", {
        confirmButtonText: "清空",
        cancelButtonText: "取消",
        type: "warning",
      });
    } catch {
      return;
    }
    const res = await clearCartApi();
    if (res && (res.success || res.code === 200)) {
      items.value = [];
      ElMessage.success("购物车已清空");
    } else {
      ElMessage.warning((res && res.msg) || "清空失败");
    }
  };

  // 结算选中的 cartItemId 集合（购物车页勾选后传入抽屉，抽屉按选中项下单；为空则全量结算）
  const checkoutIds = ref([]);

  // AI 建议使用的券（userCouponId 数组）。只作为「打开抽屉时的预选」，
  // 不直接改动当前已选，且抽屉消费一次后即清空，避免下次打开被重复硬塞
  const suggestedCouponIds = ref([]);

  const setSuggestedCoupons = (ids) => {
    suggestedCouponIds.value = Array.isArray(ids) ? ids.filter(Boolean) : [];
  };

  const clearSuggestedCoupons = () => {
    suggestedCouponIds.value = [];
  };

  /** 获取购物车明细用于下单；传入 ids 时仅返回选中项 */
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

  /**
   * 下单成功后清掉已结算的项。
   * 本地先移除让界面立刻反映，服务端清理失败不影响已提交的订单。
   */
  const checkout = async (ids = null) => {
    const data = getCartData(ids);
    const targetIds =
      ids && ids.length ? [...ids] : items.value.map((i) => i.cartItemId);
    if (ids && ids.length) {
      items.value = items.value.filter((i) => !ids.includes(i.cartItemId));
    } else {
      items.value = [];
    }
    try {
      if (ids && ids.length) {
        await Promise.all(targetIds.map((id) => removeCartItem(id)));
      } else {
        await clearCartApi();
      }
    } catch {
      // 下单已成功，购物车清理失败只影响下次展示，不打扰用户
    }
    return data;
  };

  // 登录态失效时由 request.js 调用，仅清本地状态，不发请求
  const clearState = () => {
    items.value = [];
    loaded.value = false;
  };

  /** 兼容旧调用名 */
  const reloadCart = () => fetchCart();

  const toggleCart = () => {
    isOpen.value = !isOpen.value;
  };

  const openCart = () => {
    isOpen.value = true;
  };

  const closeCart = () => {
    isOpen.value = false;
  };

  // 首次创建即拉一次，保证导航栏角标正确（未登录时 fetchCart 内部直接返回）
  if (isLoggedIn()) {
    fetchCart();
  } else {
    loaded.value = true;
  }

  return {
    items,
    isOpen,
    loaded,
    loading,
    totalCount,
    totalPrice,
    isEmpty,
    checkoutIds,
    suggestedCouponIds,
    setSuggestedCoupons,
    clearSuggestedCoupons,
    fetchCart,
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
