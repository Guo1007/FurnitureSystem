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
        <span class="cp-summary__amount">¥{{ formatPrice(props.totalAmount) }}</span>
      </div>
      <div class="cp-summary__right">
        <span class="cp-summary__label">已选</span>
        <span class="cp-summary__count"
          >{{ draft.length }} / {{ maxStackCount }} 张</span
        >
        <span class="cp-summary__save">-¥{{ formatPrice(discount) }}</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="cp-tabs">
      <el-tab-pane :label="`可用 (${available.length})`" name="ok" />
      <el-tab-pane :label="`不可用 (${unavailable.length})`" name="no" />
    </el-tabs>

    <!-- 券列表 -->
    <div class="cp-list">
      <template v-if="activeTab === 'ok'">
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
              本单可抵 ¥{{ formatPrice(estimate(c)) }}
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
            <div class="cp-card__reason">{{ c.__reason }}</div>
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
          <span v-else class="cp-footer__tip">
            {{
              maxStackCount > 1
                ? `最多可叠加 ${maxStackCount} 张券（折扣券限 1 张）`
                : "当前仅可使用 1 张券"
            }}
          </span>
        </div>
        <div class="cp-footer__btns">
          <el-button size="default" @click="useBest">最优组合</el-button>
          <el-button size="default" @click="clearAll">不使用</el-button>
          <el-button type="primary" size="default" @click="confirm">确定</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { logger } from "@/utils/logger.js";
import {
  calcTotalDiscount,
  couponReason,
  formatDay,
  isDiscount,
  pickBestCoupons,
  DEFAULT_MAX_RATIO,
  DEFAULT_MAX_STACK_COUNT,
} from "@/utils/coupon.js";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 我的券列表（getMyCoupons 原始返回） */
  coupons: { type: Array, default: () => [] },
  /** 参与结算的商品总额 */
  totalAmount: { type: Number, default: 0 },
  /**
   * 各分类商品小计：{ [typeId]: 金额 }。
   * 用于让分类券的「门槛校验 / 抵扣估算」与后端 resolveCouponBase 保持一致，
   * 不传则分类券一律按 0 基数处理（与后端 null 兜底一致）。
   */
  subTotals: { type: Object, default: () => ({}) },
  /**
   * 是否存在「分类未知」的购物车项（历史 localStorage 数据没有 typeId）。
   * 为 true 时分类券不做「本单无该分类商品」的硬判定，基数回退为整单金额，
   * 避免旧数据把分类券全部误判为不可用。
   */
  subTotalsUnknown: { type: Boolean, default: false },
  /** 已选中的 userCouponId 数组（v-model） */
  selectedIds: { type: Array, default: () => [] },
  /** 叠加规则：{ maxStackCount, maxDiscountRatio } */
  rules: { type: Object, default: () => ({}) },
});

const emit = defineEmits(["update:modelValue", "update:selectedIds", "confirm"]);

const innerVisible = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
});

const activeTab = ref("ok");
/** 弹窗内的草稿选择，点确定后才同步给外部 */
const draft = ref([]);

const maxStackCount = computed(() => {
  const n = Number(props.rules?.maxStackCount);
  return Number.isInteger(n) && n >= 1 ? n : DEFAULT_MAX_STACK_COUNT;
});
const maxRatio = computed(() => {
  const r = Number(props.rules?.maxDiscountRatio);
  return Number.isFinite(r) && r > 0 && r <= 1 ? r : DEFAULT_MAX_RATIO;
});

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      activeTab.value = "ok";
      draft.value = [...props.selectedIds];
    }
  },
);

/* ---------- 可用性判定 ---------- */
const withReason = computed(() =>
  props.coupons.map((c) => {
    const item = { ...c };
    item.__reason = couponReason(
      c,
      props.totalAmount,
      props.subTotals,
      props.subTotalsUnknown,
    );
    return item;
  }),
);

const available = computed(() => withReason.value.filter((c) => !c.__reason));
const unavailable = computed(() => withReason.value.filter((c) => c.__reason));

/* ---------- 金额估算（与后端 calcCouponsDiscount 对齐） ---------- */
const calcTotal = (list) =>
  calcTotalDiscount(
    list,
    props.totalAmount,
    props.subTotals,
    maxRatio.value,
    props.subTotalsUnknown,
  );

const selectedCoupons = computed(() =>
  draft.value
    .map((id) => available.value.find((c) => c.userCouponId === id))
    .filter(Boolean),
);

const discount = computed(() => calcTotal(selectedCoupons.value));
const payable = computed(() =>
  Math.round(Math.max(0, props.totalAmount - discount.value) * 100) / 100,
);

/** 单张券在当前已选组合基础上的预估抵扣（用于卡片展示） */
const estimate = (c) => {
  const others = selectedCoupons.value.filter((x) => x.userCouponId !== c.userCouponId);
  const withIt = calcTotal([...others, c]);
  const withoutIt = calcTotal(others);
  return Math.round(Math.max(0, withIt - withoutIt) * 100) / 100;
};

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
  if (cur.length >= maxStackCount.value) return false;
  return isStackable(c) && cur.every(isStackable);
};

/** 不可勾选的原因文案，直接显示在卡片上，避免用户点了没反应 */
const blockReason = (c) => {
  if (isOn(c)) return "";
  const cur = selectedCoupons.value;
  if (!cur.length) return "";
  if (blockedByDiscount(c)) return "折扣券每单限 1 张";
  if (cur.length >= maxStackCount.value)
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
 * 求解逻辑在 @/utils/coupon.js 的 pickBestCoupons，这里只负责把结果写回草稿态。
 * 抽出去是为了让算法可被单测，也让购物车页/抽屉将来能复用。
 */
const useBest = () => {
  const picked = pickBestCoupons({
    coupons: props.coupons,
    totalAmount: props.totalAmount,
    subTotals: props.subTotals,
    maxStackCount: maxStackCount.value,
    maxRatio: maxRatio.value,
    unknown: props.subTotalsUnknown,
  });
  draft.value = picked.map((c) => c.userCouponId);
  if (!draft.value.length) logger.log("当前没有可提升优惠的券组合");
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
