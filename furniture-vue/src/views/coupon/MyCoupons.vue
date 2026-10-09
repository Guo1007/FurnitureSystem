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
          <div v-for="item in couponList" :key="item.userCouponId" class="coupon-card mine">
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

        <div v-if="couponList.length === 0" class="empty">
          <el-empty description="还没有领取过该类型优惠券">
            <router-link to="/coupons">
              <el-button type="primary">去领券中心</el-button>
            </router-link>
          </el-empty>
        </div>

        <div class="pagination-wrapper" v-if="total > 0">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getMyCouponsPage } from "@/api/coupon";
import { logger } from "@/utils/logger.js";

const router = useRouter();
const goBack = () => router.back();

const loading = ref(true);
const couponList = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
/** 券类型 Tab，值为字符串："0" 全部 / "1" 满减 / "2" 折扣 / "3" 无门槛 */
const activeType = ref("0");

/**
 * 加载当前页。
 * 类型筛选交给后端：分页之后前端再 filter 只会筛「当前这一页」，
 * 出现「明明有满减券却提示没有」的假空态。
 */
const loadMine = async () => {
  loading.value = true;
  try {
    const res = await getMyCouponsPage({
      // "0" 代表全部，转成 0 后是 falsy，不传给后端
      type: Number(activeType.value) || undefined,
      current: currentPage.value,
      size: pageSize.value,
    });
    if (res.success || res.code === 200) {
      couponList.value = res.data?.records || [];
      total.value = Number(res.data?.total) || 0;
    } else {
      couponList.value = [];
      total.value = 0;
      // 网络层错误已由 request 拦截器统一提示，这里只处理业务错误
      ElMessage.warning(res.msg || "加载我的卡券失败");
    }
  } catch (e) {
    logger.error("加载我的券失败:", e);
    couponList.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

// 切类型回到第 1 页：否则在第 3 页切到「折扣券」可能落到一个空页
watch(activeType, () => {
  currentPage.value = 1;
  loadMine();
});

const handleSizeChange = () => {
  currentPage.value = 1;
  loadMine();
};

const handleCurrentChange = () => {
  loadMine();
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

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
</style>
