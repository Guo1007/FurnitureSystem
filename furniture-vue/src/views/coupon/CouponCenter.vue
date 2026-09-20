<template>
  <div class="coupon-center">
    <div class="page-breadcrumb">
      <button class="breadcrumb-back" @click="goBack" title="返回">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
      </button>
      <router-link to="/">首页</router-link>
      <span>/</span>
      <span class="current">领券中心</span>
    </div>

    <div class="coupon-content">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="可领券" name="claimable"></el-tab-pane>
        <el-tab-pane label="我的券" name="mine"></el-tab-pane>
      </el-tabs>

      <!-- 可领券 -->
      <template v-if="activeTab === 'claimable'">
        <div class="coupon-filters">
          <button
            v-for="f in filterChips"
            :key="f.value"
            class="filter-chip"
            :class="{ on: activeFilter === f.value }"
            @click="activeFilter = f.value"
          >
            {{ f.label }}
          </button>
        </div>

        <div v-if="loading" class="loading">加载中...</div>

        <template v-else>
          <div class="coupon-list">
            <div
              v-for="item in visibleClaimables"
              :key="item.id"
              class="coupon-card"
            >
              <div class="coupon-left">
                <span class="coupon-amount">{{ item.amountText }}</span>
                <span class="coupon-cond">
                  {{ item.minThreshold > 0 ? `满${item.minThreshold}可用` : "无门槛" }}
                </span>
              </div>
              <div class="coupon-body">
                <h4 class="coupon-name">{{ item.name }}</h4>
                <p class="coupon-meta">{{ item.typeText }} · {{ item.scopeText }}</p>
                <p class="coupon-meta">{{ item.validText }}</p>
                <p class="coupon-meta" v-if="item.perUserLimit > 1">
                  每人限领 {{ item.perUserLimit }} 张
                </p>
              </div>
              <div class="coupon-action">
                <button
                  v-if="item.state === 0"
                  class="claim-btn"
                  :disabled="claimingId === item.id"
                  @click="handleClaim(item)"
                >
                  {{ claimingId === item.id ? "领取中..." : "领取" }}
                </button>
                <span v-else class="claim-state">{{ item.stateText }}</span>
              </div>
            </div>
          </div>

          <div v-if="visibleClaimables.length === 0" class="empty">
            <el-empty description="暂无可领优惠券" />
          </div>
        </template>
      </template>

      <!-- 我的券 -->
      <template v-else>
        <div v-if="loading" class="loading">加载中...</div>
        <template v-else>
          <div class="coupon-list">
            <div v-for="item in mineList" :key="item.userCouponId" class="coupon-card mine">
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

          <div v-if="mineList.length === 0" class="empty">
            <el-empty description="还没有领取过优惠券" />
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getClaimableCoupons, getMyCoupons, claimCoupon } from "@/api/coupon";
import { logger } from "@/utils/logger.js";

const router = useRouter();
const goBack = () => router.back();

const activeTab = ref("claimable");
const activeFilter = ref(0);
const loading = ref(false);
const claimableList = ref([]);
const mineList = ref([]);
const claimingId = ref(null);

const filterChips = [
  { label: "全部", value: 0 },
  { label: "满减", value: 1 },
  { label: "折扣", value: 2 },
  { label: "无门槛", value: 3 },
];

const visibleClaimables = computed(() =>
  activeFilter.value === 0
    ? claimableList.value
    : claimableList.value.filter((c) => c.type === activeFilter.value),
);

const loadClaimable = async () => {
  loading.value = true;
  try {
    const res = await getClaimableCoupons();
    claimableList.value = (res.success || res.code === 200) ? res.data || [] : [];
  } catch (e) {
    logger.error("加载可领券失败:", e);
  } finally {
    loading.value = false;
  }
};

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

const onTabChange = () => {
  if (activeTab.value === "claimable") loadClaimable();
  else loadMine();
};

const handleClaim = async (item) => {
  claimingId.value = item.id;
  try {
    const res = await claimCoupon(item.id);
    if (res.success || res.code === 200) {
      ElMessage.success(res.msg || "领取成功");
      loadClaimable();
    } else {
      ElMessage.error(res.msg || "领取失败");
    }
  } catch (e) {
    logger.error("领取失败:", e);
  } finally {
    claimingId.value = null;
  }
};

const formatDate = (t) => {
  if (!t) return "";
  // 兼容 Jackson 数组格式与 ISO 字符串
  if (Array.isArray(t)) {
    const [y, m, d] = t;
    return `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
  }
  return String(t).slice(0, 10);
};
const tagType = (status) => (status === 1 ? "success" : status === 2 ? "info" : "warning");

onMounted(() => loadClaimable());
</script>

<style scoped lang="scss">
@import "@/styles/views/coupon-center.scss";
</style>