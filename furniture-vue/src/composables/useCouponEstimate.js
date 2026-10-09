import { computed, onScopeDispose, ref, watch } from "vue";
import { estimateOrder } from "@/api/order.js";
import { debounce } from "@/utils/debounce.js";
import { logger } from "@/utils/logger.js";

/**
 * 下单试算。
 *
 * 把「这单能优惠多少」整个交给后端算，前端不再保留任何抵扣算法 ——
 * 这是本次重构的核心：算法从前端删掉，前后端不可能再算出两个数。
 *
 * 用法（`items` 与 `couponIds` 都传 getter，而不是值，便于内部做响应式追踪）：
 *
 *   const est = useCouponEstimate({
 *     items: () => cartStore.getCartData(selectedIds.value),
 *     couponIds: () => selectedCouponIds.value,
 *   });
 *   est.refreshNow();          // 进入页面 / 打开弹窗时立即算一次
 *   est.discount.value         // 当前所选券的抵扣
 *
 * 特性：
 * - 已选券变化会**防抖**重算（用户连点勾选只发一次）
 * - 请求带序号，后发先至时丢弃过期响应，避免数字回跳
 * - 空购物车不发请求
 * - 失败时不估算（`estimate` 为 null），界面据此退化为「不显示优惠」
 *
 * @param {() => Array} getItems 购物车明细，格式 [{ furnitureId, skuId, quantity }]
 * @param {() => Array} getCouponIds 当前已选的 userCouponId 数组
 * @param {{ debounceMs?: number }} options
 */
export function useCouponEstimate(getItems, getCouponIds, options = {}) {
  const { debounceMs = 250 } = options;

  /** 是否有请求在途 */
  const estimating = ref(false);
  /** 最近一次是否失败（业务错误或网络异常） */
  const failed = ref(false);
  /** 后端返回的试算结果；为 null 表示尚无有效结果（未加载 / 失败 / 空车） */
  const estimate = ref(null);

  /** 请求序号：只有最后发出的那次请求才允许写回结果 */
  let seq = 0;

  const run = async () => {
    const items = typeof getItems === "function" ? getItems() : [];
    if (!items || items.length === 0) {
      // 空购物车不发请求；同时把在途请求的结果作废
      seq++;
      estimate.value = null;
      estimating.value = false;
      failed.value = false;
      return;
    }
    const mySeq = ++seq;
    estimating.value = true;
    failed.value = false;
    try {
      const res = await estimateOrder({
        itemList: items,
        userCouponIds:
          typeof getCouponIds === "function" ? getCouponIds() || undefined : undefined,
      });
      if (mySeq !== seq) return; // 又发了新请求，本次结果作废
      if (res && (res.success || res.code === 200)) {
        estimate.value = res.data || null;
      } else {
        estimate.value = null;
        failed.value = true;
        logger.warn("下单试算失败:", res && res.msg);
      }
    } catch (e) {
      if (mySeq !== seq) return;
      estimate.value = null;
      failed.value = true;
      logger.warn("下单试算请求异常:", e);
    } finally {
      if (mySeq === seq) estimating.value = false;
    }
  };

  const scheduled = debounce(run, debounceMs);

  /**
   * 请求指纹：把「商品明细 + 已选券」序列化成一个字符串。
   *
   * 必须这么绕一下 —— 调用方传进来的 getter 通常每次返回**新的数组**
   * （如 cartStore.getCartData(...)），直接 watch 它的话引用永远不相等，
   * 任何无关的响应式变化都会触发一次多余的试算请求。比较字符串则只在内容真的变了才发。
   */
  const requestKey = computed(() => {
    const items = typeof getItems === "function" ? getItems() : [];
    const couponIds = typeof getCouponIds === "function" ? getCouponIds() : [];
    return JSON.stringify({ i: items || [], c: couponIds || [] });
  });

  // 商品或已选券变化 → 防抖重算
  watch(requestKey, () => scheduled());

  // 组件卸载时清掉未触发的定时器，避免销毁后仍发请求
  onScopeDispose(() => scheduled.cancel());

  /** 当前所选券的抵扣金额；无结果时为 0 */
  const discount = computed(() => Number(estimate.value?.totalDiscount) || 0);

  /** 实付金额；无结果时为 0 */
  const payable = computed(() => Number(estimate.value?.payable) || 0);

  /** 商品总额（后端按数据库价格算出的，未必等于购物车里的本地价） */
  const goodsTotal = computed(() => Number(estimate.value?.goodsTotal) || 0);

  /** 后台配置的最大叠加张数；未加载时为 null（此时不限制本地勾选） */
  const maxStackCount = computed(() => {
    const n = Number(estimate.value?.maxStackCount);
    return Number.isInteger(n) && n >= 1 ? n : null;
  });

  /** userCouponId -> { usable, reason, estimate } */
  const optionMap = computed(() => {
    const m = new Map();
    for (const o of estimate.value?.couponOptions || []) {
      m.set(o.userCouponId, o);
    }
    return m;
  });

  /** 该券在本单是否可用。规则未加载时返回 true，避免把整列券误判成不可用 */
  const isUsable = (userCouponId) => {
    const o = optionMap.value.get(userCouponId);
    if (!o) return true;
    return o.usable !== false;
  };

  /** 不可用原因；可用或未加载时为空串 */
  const reasonOf = (userCouponId) => {
    const o = optionMap.value.get(userCouponId);
    return o && o.usable === false ? o.reason || "不可用" : "";
  };

  /** 该券在本单的边际抵扣（卡片上的「本单可抵 ¥X」）；未加载时为 0 */
  const estimateOf = (userCouponId) => {
    const o = optionMap.value.get(userCouponId);
    return Number(o?.estimate) || 0;
  };

  /** 最优组合推荐；未加载时为空数组 */
  const bestUserCouponIds = computed(() => estimate.value?.bestUserCouponIds || []);

  /** 是否已经有有效结果（用于区分「还没算出来」和「算出来是 0」） */
  const loaded = computed(() => estimate.value != null);

  return {
    estimating,
    failed,
    loaded,
    estimate,
    discount,
    payable,
    goodsTotal,
    maxStackCount,
    bestUserCouponIds,
    isUsable,
    reasonOf,
    estimateOf,
    /** 立即重算（不防抖）：进入页面、打开弹窗时用 */
    refreshNow: run,
    /** 防抖重算：连续变化时用 */
    scheduleRefresh: scheduled,
  };
}
