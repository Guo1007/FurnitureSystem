<template>
  <el-dialog
    v-model="innerVisible"
    title="选择优惠券"
    width="580px"
    top="6vh"
    :close-on-click-modal="false"
    class="coupon-picker"
    append-to-body
  >
    <!-- 汇总条 -->
    <div class="cp-summary">
      <div class="cp-summary__left">
        <span class="cp-summary__label">商品金额</span>
        <span class="cp-summary__amount">{{ goodsTotalText }}</span>
      </div>
      <div class="cp-summary__right">
        <span class="cp-summary__label">已选</span>
        <span class="cp-summary__count">{{ selectedCountText }}</span>
        <span v-if="discount > 0" class="cp-summary__save"
          >-¥{{ formatPrice(discount) }}</span
        >
      </div>
    </div>

    <el-tabs v-model="activeTab" class="cp-tabs">
      <el-tab-pane :label="`可用 (${available.length})`" name="ok" />
      <el-tab-pane :label="`不可用 (${unavailable.length})`" name="no" />
    </el-tabs>

    <!-- 券列表 -->
    <div class="cp-list">
      <!-- 首次计算：还没有结果，先挡一层，免得券的可用性"先全可用、拿到结果再翻转" -->
      <div v-if="!listReady" class="cp-empty">
        <div class="cp-empty__text">正在计算优惠…</div>
      </div>

      <template v-else-if="activeTab === 'ok'">
        <div v-if="!available.length" class="cp-empty">
          <div class="cp-empty__icon">🎫</div>
          <div class="cp-empty__text">暂无可用优惠券</div>
          <router-link to="/coupons" class="cp-empty__link" @click="close"
            >去领券中心</router-link
          >
        </div>

        <div
          v-for="c in available"
          :key="c.userCouponId"
          class="cp-card"
          :class="{
            'is-on': isOn(c),
            'is-disabled': !canCheck(c),
          }"
          @click="toggle(c)"
        >
          <!-- 左侧面额 -->
          <div class="cp-card__face">
            <div class="cp-card__value">{{ faceValue(c) }}</div>
            <div class="cp-card__cond">
              {{ Number(c.minThreshold) > 0 ? `满${c.minThreshold}` : "无门槛" }}
            </div>
          </div>

          <!-- 右侧信息 -->
          <div class="cp-card__body">
            <div class="cp-card__name">
              {{ c.name }}
              <span class="cp-tag" :class="c.stackable == 1 ? 'ok' : 'no'">
                {{ c.stackable == 1 ? "可叠加" : "不可叠加" }}
              </span>
            </div>
            <div class="cp-card__meta">
              <span>{{ c.scopeText || "全场通用" }}</span>
              <span v-if="formatDate(c.expireTime)">· {{ formatDate(c.expireTime) }} 到期</span>
            </div>
            <div v-if="blockReason(c)" class="cp-card__reason">
              {{ blockReason(c) }}
            </div>
            <div v-else class="cp-card__save">
              {{ amountText(c) }}
            </div>
          </div>

          <!-- 勾选 -->
          <div class="cp-card__check" :class="{ 'is-on': isOn(c) }">
            <span v-if="isOn(c)">✓</span>
          </div>
        </div>
      </template>

      <template v-else>
        <div v-if="!unavailable.length" class="cp-empty">
          <div class="cp-empty__text">没有不可用优惠券</div>
        </div>
        <div v-for="c in unavailable" :key="c.userCouponId" class="cp-card is-grey">
          <div class="cp-card__face">
            <div class="cp-card__value">{{ faceValue(c) }}</div>
            <div class="cp-card__cond">
              {{ Number(c.minThreshold) > 0 ? `满${c.minThreshold}` : "无门槛" }}
            </div>
          </div>
          <div class="cp-card__body">
            <div class="cp-card__name">{{ c.name }}</div>
            <div class="cp-card__meta">{{ c.scopeText || "全场通用" }}</div>
            <div class="cp-card__reason">{{ couponReasonOf(c) }}</div>
          </div>
          <div class="cp-card__check is-lock">×</div>
        </div>
      </template>
    </div>

    <!-- 底部操作 -->
    <template #footer>
      <div class="cp-footer">
        <div class="cp-footer__info">
          <span v-if="discount > 0">
            已优惠 <b class="cp-footer__save">¥{{ formatPrice(discount) }}</b>
            ，实付 ¥{{ formatPrice(payable) }}
          </span>
          <span v-else class="cp-footer__tip">{{ stackTip }}</span>
        </div>
        <div class="cp-footer__btns">
          <el-button size="default" :disabled="!loaded" @click="useBest"
            >最优组合</el-button
          >
          <el-button size="default" @click="clearAll">不使用</el-button>
          <el-button type="primary" size="default" @click="confirm">确定</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { formatDay, isDiscount } from "@/utils/coupon.js";
import { useCouponEstimate } from "@/composables/useCouponEstimate.js";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 我的券列表（getMyCoupons 原始返回） */
  coupons: { type: Array, default: () => [] },
  /**
   * 参与结算的商品明细，格式 [{ furnitureId, skuId, quantity }]。
   *
   * 只传「买什么、买几件」——单价、总额、抵扣一律由后端试算。这样试算与下单
   * 走的是同一套算价逻辑，前端也没有任何可与之脱钩的第二份实现。
   * （原先的 totalAmount / subTotals / subTotalsUnknown 三个 prop 因此全部取消：
   *   分类小计与「分类未知」的兜底都由后端从数据库直接算，前端不需要再猜。）
   */
  items: { type: Array, default: () => [] },
  /** 已选中的 userCouponId 数组（v-model） */
  selectedIds: { type: Array, default: () => [] },
});

const emit = defineEmits(["update:modelValue", "update:selectedIds", "confirm"]);

const innerVisible = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
});

const activeTab = ref("ok");
/** 弹窗内的草稿选择，点确定后才同步给外部 */
const draft = ref([]);

/**
 * 金额全部来自后端试算，前端不再有任何抵扣算法。
 * 用**草稿**选择去试算，这样弹窗里的「已优惠 / 实付 / 本单可抵」反映的是眼前这次勾选，
 * 而不是外部已确认的那份。
 */
// 解构出来而不是保留 est.xxx：模板里访问普通对象的嵌套 ref 不会自动解包，
// 解构到顶层后模板可直接用 loaded / discount / payable 等名字。
const {
  failed,
  loaded,
  goodsTotal,
  discount,
  payable,
  maxStackCount,
  bestUserCouponIds,
  isUsable,
  reasonOf,
  estimateOf,
  refreshNow,
} = useCouponEstimate(() => props.items, () => draft.value);

/**
 * 降级态：试算失败且拿不到结果。
 * 此时不猜金额，券一律按可用展示（isUsable 在缺少评估时返回 true），
 * 让用户仍能手动选券，最终以结算为准。
 */
const degraded = computed(() => !loaded.value && failed.value);

/** 券列表是否可以渲染：拿到了结果，或已确认失败（降级展示） */
const listReady = computed(() => loaded.value || degraded.value);

/** 顶部「已选」计数：张数未知时不显示分母，避免渲染出「1 / null 张」 */
const selectedCountText = computed(() =>
  maxStackCount.value == null
    ? `${draft.value.length} 张`
    : `${draft.value.length} / ${maxStackCount.value} 张`,
);

/** 底部提示 */
const stackTip = computed(() => {
  if (degraded.value) return "优惠金额暂时算不出来，以结算为准";
  if (maxStackCount.value == null) return "优惠金额计算中…";
  return maxStackCount.value > 1
    ? `最多可叠加 ${maxStackCount.value} 张券（折扣券限 1 张）`
    : "当前仅可使用 1 张券";
});

/** 商品金额：以后端试算为准（前端购物车里缓存的价格可能已过期） */
const goodsTotalText = computed(() =>
  loaded.value ? `¥${formatPrice(goodsTotal.value)}` : "计算中…",
);

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      activeTab.value = "ok";
      draft.value = [...props.selectedIds];
      // 打开即算一次，不让用户干等防抖
      refreshNow();
    }
  },
);

/* ---------- 可用性判定（全部由后端裁定） ---------- */
/**
 * 可用 / 不可用完全由后端试算裁定 —— 门槛、适用范围、有效期、配置异常都在那边判，
 * 前端只负责分组展示。结果没回来时 est.isUsable 一律返回 true，
 * 不会把一整列券误判成不可用。
 */
const available = computed(() =>
  props.coupons.filter((c) => isUsable(c.userCouponId)),
);
const unavailable = computed(() =>
  props.coupons.filter((c) => !isUsable(c.userCouponId)),
);

/** 不可用原因（后端给的），如「差 ¥50 可用」「本单无该分类商品」 */
const couponReasonOf = (c) => reasonOf(c.userCouponId);

/** 已选券对象（只取可用的，与后端「只认可用券」的口径一致） */
const selectedCoupons = computed(() =>
  draft.value
    .map((id) => available.value.find((c) => c.userCouponId === id))
    .filter(Boolean),
);

/* ---------- 金额（全部来自后端） ---------- */

/** 卡片上的「本单可抵」：结果没回来时不编数字 */
const amountText = (c) =>
  loaded.value
    ? `本单可抵 ¥${formatPrice(estimateOf(c.userCouponId))}`
    : "本单可抵金额待计算";

/* ---------- 选择交互 ---------- */
const isOn = (c) => draft.value.includes(c.userCouponId);
const isStackable = (c) => Number(c.stackable) === 1;

/** 折扣券每单限 1 张：已选了折扣券时，其余折扣券一律不能再勾 */
const blockedByDiscount = (c) =>
  isDiscount(c) && selectedCoupons.value.some(isDiscount);

/** 是否允许勾选：折扣券限 1 张；不可叠加券与已选其它券互斥；已达张数上限 */
const canCheck = (c) => {
  if (isOn(c)) return true;
  const cur = selectedCoupons.value;
  if (!cur.length) return true;
  if (blockedByDiscount(c)) return false;
  // 规则未加载（maxStackCount 为 null）时不限制张数，交由后端在结算时裁定
  if (maxStackCount.value != null && cur.length >= maxStackCount.value)
    return false;
  return isStackable(c) && cur.every(isStackable);
};

/** 不可勾选的原因文案，直接显示在卡片上，避免用户点了没反应 */
const blockReason = (c) => {
  if (isOn(c)) return "";
  const cur = selectedCoupons.value;
  if (!cur.length) return "";
  if (blockedByDiscount(c)) return "折扣券每单限 1 张";
  if (maxStackCount.value != null && cur.length >= maxStackCount.value)
    return `最多叠加 ${maxStackCount.value} 张`;
  if (!isStackable(c)) return "该券不可与已选券同用";
  if (!cur.every(isStackable)) return "已选了不可叠加券";
  return "";
};

const toggle = (c) => {
  if (!canCheck(c)) return;
  if (isOn(c)) {
    draft.value = draft.value.filter((id) => id !== c.userCouponId);
    return;
  }
  if (isStackable(c)) {
    // 可叠加券：清掉已选的不可叠加券；若本次勾的是折扣券，再清掉其它折扣券
    const keep = selectedCoupons.value.filter(isStackable);
    const rest = isDiscount(c) ? keep.filter((x) => !isDiscount(x)) : keep;
    draft.value = [...rest.map((x) => x.userCouponId), c.userCouponId];
  } else {
    // 不可叠加券：独占
    draft.value = [c.userCouponId];
  }
};

/* ---------- 一键最优 ---------- */
/**
 * 最优组合的搜索在后端（只有它知道券的真实可用性与叠加规则），
 * 前端只负责把返回的 userCouponId 列表写回草稿态。
 */
const useBest = () => {
  if (!loaded.value) return;
  const picked = [...bestUserCouponIds.value];
  draft.value = picked;
  if (!picked.length) ElMessage.info("当前没有能进一步省钱的券组合");
};

const clearAll = () => {
  draft.value = [];
};

const confirm = () => {
  emit("update:selectedIds", [...draft.value]);
  emit("confirm", [...draft.value]);
  innerVisible.value = false;
};

const close = () => {
  innerVisible.value = false;
};

/* ---------- 展示 ---------- */
const faceValue = (c) => {
  if (c.type === 2 && c.discount) {
    const d = Number(c.discount) * 10;
    return `${Number.isInteger(d) ? d : d.toFixed(1)}折`;
  }
  return `¥${formatPrice(c.amount || 0)}`;
};
const formatPrice = (v) => {
  const n = Number(v) || 0;
  return n.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};
const formatDate = (t) => formatDay(t);
</script>

<style scoped lang="scss">
.cp-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  margin-bottom: 4px;
  border-radius: 8px;
  background: #fff7f0;

  &__label {
    margin-right: 6px;
    font-size: 12px;
    color: #909399;
  }
  &__amount {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
  &__count {
    font-size: 13px;
    color: #606266;
  }
  &__save {
    margin-left: 10px;
    font-size: 16px;
    font-weight: 700;
    color: #e4393c;
  }
}

.cp-tabs {
  :deep(.el-tabs__header) {
    margin: 0 0 8px;
  }
  :deep(.el-tabs__nav-wrap)::after {
    height: 1px;
  }
}

.cp-list {
  max-height: 46vh;
  overflow-y: auto;
  padding: 2px;
}

.cp-card {
  position: relative;
  display: flex;
  align-items: stretch;
  margin-bottom: 10px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover {
    border-color: #f5c6a5;
    box-shadow: 0 2px 10px rgba(228, 57, 60, 0.08);
  }

  &.is-on {
    border-color: #e4393c;
    box-shadow: 0 2px 12px rgba(228, 57, 60, 0.14);
  }

  &.is-disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &.is-grey {
    background: #fafafa;
    cursor: default;
    &:hover {
      border-color: #ebeef5;
      box-shadow: none;
    }
  }

  &__face {
    flex: 0 0 104px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    padding: 14px 8px;
    color: #fff;
    background: linear-gradient(135deg, #ff7a45 0%, #e4393c 100%);
    position: relative;

    &::after {
      content: "";
      position: absolute;
      right: 0;
      top: 8px;
      bottom: 8px;
      border-right: 1px dashed rgba(255, 255, 255, 0.6);
    }
  }

  &.is-grey &__face {
    background: #c0c4cc;
  }

  &__value {
    font-size: 22px;
    font-weight: 700;
    line-height: 1.1;
    white-space: nowrap;
  }

  &__cond {
    font-size: 11px;
    opacity: 0.9;
  }

  &__body {
    flex: 1;
    min-width: 0;
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 4px;
  }

  &__name {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  &__meta {
    font-size: 12px;
    color: #909399;
  }

  &__save {
    font-size: 12px;
    color: #e4393c;
  }

  &__reason {
    font-size: 12px;
    color: #c0c4cc;
  }

  &__check {
    flex: 0 0 46px;
    display: flex;
    align-items: center;
    justify-content: center;

    span {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #e4393c;
      color: #fff;
      font-size: 13px;
    }

    &:not(.is-on) {
      &::before {
        content: "";
        width: 19px;
        height: 19px;
        border: 1px solid #dcdfe6;
        border-radius: 50%;
      }
    }

    &.is-lock {
      color: #c0c4cc;
      font-size: 18px;
    }
  }
}

.cp-tag {
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 400;
  &.ok {
    color: #e4393c;
    background: #fef0f0;
  }
  &.no {
    color: #909399;
    background: #f4f4f5;
  }
}

.cp-empty {
  padding: 40px 0;
  text-align: center;
  &__icon {
    font-size: 34px;
    opacity: 0.5;
  }
  &__text {
    margin-top: 8px;
    font-size: 13px;
    color: #909399;
  }
  &__link {
    display: inline-block;
    margin-top: 10px;
    font-size: 13px;
    color: #e4393c;
    text-decoration: none;
  }
}

.cp-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;

  &__info {
    font-size: 13px;
    color: #606266;
  }
  &__save {
    color: #e4393c;
    font-size: 15px;
  }
  &__tip {
    color: #909399;
    font-size: 12px;
  }
  &__btns {
    display: flex;
    gap: 8px;
  }
}
</style>
