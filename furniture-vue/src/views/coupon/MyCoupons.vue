<template>
  <div class="coupon-center">
    <div class="page-breadcrumb">
      <button class="breadcrumb-back" @click="goBack" title="返回">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
      </button>
      <router-link to="/">首页</router-link>
      <span>/</span>
      <router-link to="/user/profile">个人中心</router-link>
      <span>/</span>
      <span class="current">我的卡券</span>
    </div>

    <div class="coupon-content">
      <h2 class="my-coupon-title">我的卡券</h2>

      <el-tabs v-model="activeType">
        <el-tab-pane label="全部" name="0"></el-tab-pane>
        <el-tab-pane label="满减券" name="1"></el-tab-pane>
        <el-tab-pane label="折扣券" name="2"></el-tab-pane>
        <el-tab-pane label="无门槛券" name="3"></el-tab-pane>
      </el-tabs>

      <div v-if="loading" class="loading">加载中...</div>

      <template v-else>
        <div class="coupon-list">
          <div v-for="item in visibleCoupons" :key="item.userCouponId" class="coupon-card mine">
            <div class="coupon-left">
              <span class="coupon-amount">{{ item.amountText }}</span>
              <span class="coupon-cond">
                {{ item.minThreshold > 0 ? `满${item.minThreshold}可用` : "无门槛" }}
              </span>
            </div>
            <div class="coupon-body">
              <h4 class="coupon-name">{{ item.name }}</h4>
              <p class="coupon-meta">{{ item.scopeText }}</p>
              <p class="coupon-meta" v-if="item.gotTime">领取于 {{ formatDate(item.gotTime) }}</p>
              <p class="coupon-meta" v-if="item.expireTime">有效至 {{ formatDate(item.expireTime) }}</p>
            </div>
            <div class="coupon-action">
              <el-tag :type="tagType(item.status)" effect="plain">{{ item.statusText }}</el-tag>
            </div>
          </div>
        </div>

        <div v-if="visibleCoupons.length === 0" class="empty">
          <el-empty description="还没有领取过该类型优惠券">
            <router-link to="/coupons">
              <el-button type="primary">去领券中心</el-button>
            </router-link>
          </el-empty>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { getMyCoupons } from "@/api/coupon";
import { logger } from "@/utils/logger.js";

const router = useRouter();
const goBack = () => router.back();

const loading = ref(false);
const mineList = ref([]);
const activeType = ref("0");

const visibleCoupons = computed(() =>
  Number(activeType.value) === 0
    ? mineList.value
    : mineList.value.filter((c) => c.type === Number(activeType.value)),
);

const loadMine = async () => {
  loading.value = true;
  try {
    const res = await getMyCoupons();
    mineList.value = (res.success || res.code === 200) ? res.data || [] : [];
  } catch (e) {
    logger.error("加载我的券失败:", e);
  } finally {
    loading.value = false;
  }
};

const formatDate = (t) => {
  if (!t) return "";
  if (Array.isArray(t)) {
    const [y, m, d] = t;
    return `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
  }
  return String(t).slice(0, 10);
};
const tagType = (status) => (status === 1 ? "success" : status === 2 ? "info" : "warning");

onMounted(loadMine);
</script>

<style scoped lang="scss">
@import "@/styles/views/coupon-center.scss";

.my-coupon-title {
  margin: 4px 0 8px;
  font-size: var(--text-2xl);
  color: var(--color-text-primary);
}
</style>