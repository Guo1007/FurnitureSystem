<template>
  <div class="dashboard-page">
    <h2 class="page-title">数据概览</h2>

    <div class="stat-cards" v-loading="statsLoading">
      <div class="stat-card">
        <div class="stat-icon users">
          <el-icon :size="24">
            <User />
          </el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-number">{{ stats.userCount }}</div>
          <div class="stat-label">用户总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon furniture">
          <el-icon :size="24">
            <Present />
          </el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-number">{{ stats.furnitureCount }}</div>
          <div class="stat-label">家具总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon orders">
          <el-icon :size="24">
            <Document />
          </el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-number">{{ stats.orderCount }}</div>
          <div class="stat-label">订单总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon revenue">
          <el-icon :size="24">
            <Money />
          </el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-number">¥{{ totalAmountDisplay }}</div>
          <div class="stat-label">成交总额</div>
        </div>
      </div>
    </div>

    <div class="chart-section">
      <el-card class="chart-card">
        <template #header>
          <span class="card-header">近7天订单趋势</span>
        </template>
        <div v-loading="trendLoading" class="chart-body">
          <div
            v-if="orderTrend.length === 0 && !trendLoading"
            class="chart-empty"
          >
            暂无订单数据
          </div>
          <div v-else class="bar-chart">
            <div class="bar-item" v-for="item in orderTrend" :key="item.date">
              <div class="bar-value">{{ item.count }}</div>
              <div class="bar-fill-wrap">
                <div
                  class="bar-fill"
                  :style="{ height: barHeight(item.count) }"
                ></div>
              </div>
              <div class="bar-label">{{ item.date.slice(5) }}</div>
            </div>
          </div>
        </div>
      </el-card>
      <el-card class="chart-card">
        <template #header>
          <span class="card-header">热销家具 TOP5</span>
        </template>
        <div v-loading="topLoading" class="chart-body">
          <div
            v-if="topFurniture.length === 0 && !topLoading"
            class="chart-empty"
          >
            暂无销售数据
          </div>
          <div v-else class="rank-list">
            <div
              class="rank-item"
              v-for="(item, index) in topFurniture"
              :key="item.furnitureId"
            >
              <span class="rank-num" :class="'rank-' + (index + 1)">{{
                index + 1
              }}</span>
              <img
                loading="lazy"
                :src="
                  imgUrl(item.furnitureIcon, '/images/default-furniture.png')
                "
                class="rank-img"
                @error="handleRankImgError"
              />
              <div class="rank-info">
                <span class="rank-name">{{ item.furnitureName }}</span>
              </div>
              <div class="rank-sold">
                <span class="sold-count">{{ item.totalSold }}</span>
                <span class="sold-label">件已售</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <el-card class="low-stock-card" v-if="lowStockList.length > 0">
      <template #header>
        <div class="low-stock-header">
          <div class="low-stock-title">
            <span class="low-stock-dot"></span>
            <span class="card-header">库存预警</span>
            <span class="low-stock-count">{{ lowStockList.length }} 种库存不足</span>
          </div>
          <el-button
            v-if="lowStockList.length > LOW_STOCK_LIMIT"
            type="primary"
            plain
            size="small"
            @click="openLowStockDrawer"
          >
            查看全部
            <el-icon style="margin-left: 2px"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </template>

      <!-- 首页精简卡片：网格展示前 6 个 -->
      <div class="low-stock-grid">
        <div
          class="low-stock-tile"
          v-for="item in visibleLowStock"
          :key="item.id"
          :class="{ soldout: item.stock === 0 }"
        >
          <div class="tile-thumb">
            <img
              loading="lazy"
              :src="imgUrl(item.fIcon, '/images/default-furniture.png')"
              alt=""
              @error="handleLowStockImgError"
            />
          </div>
          <div class="tile-body">
            <span class="tile-name">{{ item.fName }}</span>
            <span class="tile-type">{{ item.typeName || "未分类" }}</span>
          </div>
          <div class="tile-stock" :class="item.stock === 0 ? 'is-out' : 'is-low'">
            {{ item.stock === 0 ? "售罄" : "剩 " + item.stock }}
          </div>
        </div>
      </div>
    </el-card>

    <!-- 库存不足详情抽屉 -->
    <el-drawer
      v-model="lowStockDrawerVisible"
      title="库存预警明细"
      size="55%"
      :with-header="true"
      class="low-stock-drawer"
    >
      <div class="drawer-content">
        <!-- 筛选条 -->
        <div class="drawer-filter">
          <el-segmented
            v-model="stockFilter"
            :options="stockFilterOptions"
            size="default"
          />
          <el-select
            v-model="typeFilter"
            placeholder="全部种类"
            clearable
            size="default"
            style="width: 150px"
          >
            <el-option
              v-for="t in typeOptions"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>
        </div>

        <!-- 结果统计 -->
        <div class="drawer-summary">
          共 <b>{{ filteredLowStock.length }}</b> 种符合筛选
        </div>

        <!-- 商品列表 -->
        <div class="drawer-list" v-if="filteredLowStock.length > 0">
          <div
            class="drawer-item"
            v-for="item in filteredLowStock"
            :key="item.id"
            :class="{ 'is-out': item.stock === 0 }"
          >
            <div class="d-item-thumb">
              <img
                loading="lazy"
                :src="imgUrl(item.fIcon, '/images/default-furniture.png')"
                alt=""
                @error="handleLowStockImgError"
              />
            </div>
            <div class="d-item-info">
              <div class="d-item-name">{{ item.fName }}</div>
              <div class="d-item-meta">
                <span class="d-item-type">{{ item.typeName || "未分类" }}</span>
              </div>
            </div>
            <div class="d-item-right">
              <span
                class="d-item-stock"
                :class="item.stock === 0 ? 'is-out' : 'is-low'"
              >
                {{ item.stock === 0 ? "已售罄" : "仅剩 " + item.stock + " 件" }}
              </span>
              <div class="d-item-bar">
                <span
                  class="bar-fill"
                  :class="item.stock === 0 ? 'is-out' : 'is-low'"
                  :style="{ width: stockPercent(item.stock) + '%' }"
                ></span>
              </div>
            </div>
          </div>
        </div>

        <el-empty
          v-else
          description="没有符合筛选的库存不足商品"
          :image-size="80"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import {
  ArrowRight,
  Document,
  Money,
  Present,
  User,
} from "@element-plus/icons-vue";
import {
  getDashboardStats,
  getLowStock,
  getOrderTrend,
  getTopFurniture,
} from "@/api/admin/dashboard.js";
import { imgUrl } from "@/utils/img.js";

const statsLoading = ref(false);
const stats = ref({
  userCount: 0,
  furnitureCount: 0,
  orderCount: 0,
  totalAmount: 0,
});

const totalAmountDisplay = computed(() => {
  const num = Number(stats.value.totalAmount);
  return num.toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
});

const trendLoading = ref(false);
const orderTrend = ref([]);

const maxTrendCount = computed(() => {
  const nums = orderTrend.value.map((i) => i.count);
  return nums.length > 0 ? Math.max(...nums) : 0;
});

const barHeight = (count) => {
  if (maxTrendCount.value === 0) return "8%";
  const pct = (count / maxTrendCount.value) * 100;
  return Math.max(pct, count > 0 ? 4 : 0) + "%";
};

const topLoading = ref(false);
const topFurniture = ref([]);

const lowStockList = ref([]);

// 库存预警：首页默认只显示前 6 个，点击"查看全部"打开抽屉展示明细
const LOW_STOCK_LIMIT = 6;
const visibleLowStock = computed(() => lowStockList.value.slice(0, LOW_STOCK_LIMIT));

// ===== 库存预警抽屉 =====
const lowStockDrawerVisible = ref(false);
const stockFilter = ref("全部");
const typeFilter = ref("");

const stockFilterOptions = ["全部", "已售罄", "库存告警"];

// 分类选项：从数据中动态提取去重
const typeOptions = computed(() => {
  const set = new Set(lowStockList.value.map((i) => i.typeName).filter(Boolean));
  return [...set];
});

const openLowStockDrawer = () => {
  stockFilter.value = "全部";
  typeFilter.value = "";
  lowStockDrawerVisible.value = true;
};

// 筛选后的列表
const filteredLowStock = computed(() => {
  return lowStockList.value.filter((item) => {
    if (stockFilter.value === "已售罄" && item.stock !== 0) return false;
    if (stockFilter.value === "库存告警" && (item.stock === 0 || item.stock >= 10)) return false;
    if (typeFilter.value && item.typeName !== typeFilter.value) return false;
    return true;
  });
});

// 库存占比条：0→100%，满库存 10 → 100%
const stockPercent = (stock) => {
  const p = Math.max(0, Math.min(100, (stock / 10) * 100));
  return p;
};

const handleRankImgError = (e) => {
  e.target.src = "/images/default-furniture.png";
};

const handleLowStockImgError = (e) => {
  e.target.style.display = "none";
};

onMounted(async () => {
  statsLoading.value = true;
  trendLoading.value = true;
  topLoading.value = true;

  try {
    const [statsRes, trendRes, topRes, lowRes] = await Promise.all([
      getDashboardStats().catch((err) => ({ success: false, error: err })),
      getOrderTrend().catch((err) => ({ success: false, error: err })),
      getTopFurniture().catch((err) => ({ success: false, error: err })),
      getLowStock().catch((err) => ({ success: false, error: err })),
    ]);

    if (statsRes.success && statsRes.data) {
      stats.value = statsRes.data;
    }

    if (trendRes.success && Array.isArray(trendRes.data)) {
      orderTrend.value = trendRes.data;
    }

    if (topRes.success && Array.isArray(topRes.data)) {
      topFurniture.value = topRes.data;
    }

    if (lowRes.success && Array.isArray(lowRes.data)) {
      lowStockList.value = lowRes.data;
    }
  } finally {
    statsLoading.value = false;
    trendLoading.value = false;
    topLoading.value = false;
  }
});
</script>

<style scoped lang="scss">
@import "@/styles/views/admin-admin-dashboard.scss";
</style>
