<template>
  <div class="category-page">
    <div class="category-container">
      <!-- Breadcrumb -->
      <div class="breadcrumb">
        <button class="breadcrumb-back" @click="goBack" title="返回">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
        </button>
        <router-link to="/">首页</router-link>
        <span>/</span>
        <span class="current">{{ typeInfo.name || "全部商品" }}</span>
      </div>

      <!-- Category Header -->
      <div class="cat-banner" v-if="typeInfo.name">
        <div>
          <h1 class="cat-title">{{ typeInfo.name }}</h1>
          <p class="cat-desc">
            {{
              typeInfo.title ||
              "探索" + typeInfo.name + "系列的极致工艺与舒适体验"
            }}
          </p>
        </div>
      </div>

      <!-- Toolbar -->
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="total-count">共 {{ total }} 件商品</span>
        </div>
        <div class="toolbar-right">
          <div class="sort-group">
            <button
              v-for="s in sortOptions"
              :key="s.value"
              class="sort-btn"
              :class="{ active: sortBy === s.value }"
              @click="setSort(s.value)"
            >
              {{ s.label }}
            </button>
          </div>
          <div class="view-toggle">
            <button
              class="view-btn"
              :class="{ active: viewMode === 'grid' }"
              @click="viewMode = 'grid'"
              title="网格视图"
            >
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="currentColor"
              >
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="7" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="14" width="7" height="7" rx="1" />
              </svg>
            </button>
            <button
              class="view-btn"
              :class="{ active: viewMode === 'list' }"
              @click="viewMode = 'list'"
              title="列表视图"
            >
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="currentColor"
              >
                <rect x="3" y="4" width="18" height="3" rx="1" />
                <rect x="3" y="10" width="18" height="3" rx="1" />
                <rect x="3" y="16" width="18" height="3" rx="1" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <div class="cat-layout" :class="{ 'with-sidebar': showSidebar }">
        <!-- Sidebar filters (desktop) -->
        <aside class="filter-sidebar" v-if="showSidebar">
          <div class="filter-block">
            <h4 class="filter-title">搜索</h4>
            <el-input
              v-model="searchForm.fName"
              placeholder="输入名称关键词..."
              clearable
              size="default"
              @keyup.enter="handleSearch"
              @clear="searchForm.fName = ''"
            />
          </div>

          <div class="filter-block">
            <h4 class="filter-title">品牌</h4>
            <div class="filter-scroll">
              <label
                v-for="brand in brandOptions"
                :key="brand.value"
                class="filter-checkbox"
              >
                <input
                  type="radio"
                  :value="brand.value"
                  v-model="searchForm.brand"
                  @change="handleSearch"
                  name="brand"
                />
                <span class="filter-checkmark"></span>
                <span class="filter-label">{{ brand.label }}</span>
              </label>
            </div>
            <button
              v-if="searchForm.brand"
              class="filter-clear"
              @click="
                searchForm.brand = undefined;
                handleSearch();
              "
            >
              清除品牌筛选
            </button>
          </div>

          <div class="filter-block">
            <h4 class="filter-title">库存状态</h4>
            <label class="filter-checkbox">
              <input
                type="radio"
                value=""
                v-model="searchForm.stockStatus"
                @change="handleSearch"
                name="stock"
              />
              <span class="filter-checkmark"></span>
              <span class="filter-label">全部</span>
            </label>
            <label class="filter-checkbox">
              <input
                type="radio"
                value="in_stock"
                v-model="searchForm.stockStatus"
                @change="handleSearch"
                name="stock"
              />
              <span class="filter-checkmark"></span>
              <span class="filter-label">有库存</span>
            </label>
            <label class="filter-checkbox">
              <input
                type="radio"
                value="low_stock"
                v-model="searchForm.stockStatus"
                @change="handleSearch"
                name="stock"
              />
              <span class="filter-checkmark"></span>
              <span class="filter-label">库存紧张</span>
            </label>
          </div>

          <button class="filter-reset" @click="resetSearch">
            重置所有筛选
          </button>
        </aside>

        <!-- Main content -->
        <div class="cat-main">
          <!-- Loading -->
          <div v-if="loading" class="loading-state">
            <div class="skeleton-grid" :class="viewMode">
              <div class="skeleton-card" v-for="i in 8" :key="i">
                <div class="sk-img"></div>
                <div class="sk-info">
                  <div class="sk-line short"></div>
                  <div class="sk-line"></div>
                  <div class="sk-line medium"></div>
                </div>
              </div>
            </div>
          </div>

          <!-- Empty -->
          <div v-else-if="furnitureList.length === 0" class="empty-state">
            <span class="empty-icon">📦</span>
            <p>该分类暂无家具</p>
            <p class="empty-tip" v-if="hasActiveFilters">
              当前筛选条件无匹配结果
            </p>
            <button
              class="clear-filters-btn"
              v-if="hasActiveFilters"
              @click="resetSearchAndLoad"
            >
              清除筛选条件
            </button>
            <router-link to="/" class="back-home-btn" v-else
              >去其他分类看看</router-link
            >
          </div>

          <!-- Grid view -->
          <div v-else-if="viewMode === 'grid'" class="product-grid">
            <ProductCard
              v-for="item in furnitureList"
              :key="item.id"
              :product="item"
            />
          </div>

          <!-- List view -->
          <div v-else class="product-list">
            <div
              class="list-item"
              v-for="item in furnitureList"
              :key="item.id"
              @click="goToDetail(item)"
            >
              <div class="list-img-wrap">
                <img
                  :src="imgUrl(item.fIcon)"
                  :alt="item.fName"
                  class="list-img"
                  @error="handleImgError"
                />
                <div class="list-badges">
                  <span
                    class="badge badge-low"
                    v-if="item.stock > 0 && item.stock < 10"
                    >库存紧张</span
                  >
                  <span class="badge badge-out" v-if="item.stock === 0"
                    >暂时缺货</span
                  >
                </div>
              </div>
              <div class="list-info">
                <p class="list-brand" v-if="item.brand">{{ item.brand }}</p>
                <h3 class="list-name">{{ item.fName }}</h3>
                <p class="list-intro" v-if="item.intro">{{ item.intro }}</p>
                <div class="list-footer">
                  <span class="list-price">¥{{ formatPrice(item.price) }}</span>
                  <button
                    class="list-cart-btn"
                    @click.stop="quickAdd(item)"
                    :disabled="item.stock === 0"
                  >
                    加入购物车
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Pagination -->
          <div class="pagination-wrapper" v-if="total > 0 && !loading">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[12, 24, 48]"
              layout="total, sizes, prev, pager, next"
              :total="total"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </div>
      </div>
    </div>

    <SpecSelectDialog
      v-model:visible="specDialogVisible"
      :product="specTarget || {}"
      :spec-groups="specData.specGroups"
      :sku-list="specData.skuList"
      @confirm="onSpecConfirm"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getFurnitureBrands, getFurnitureByTypeId, getFurnitureSpecs } from "@/api/furniture.js";
import { imgUrl } from "@/utils/img.js";
import { formatPrice } from "@/utils/format.js";
import { logger } from "@/utils/logger.js";
import { useCartStore } from "@/stores/cart.js";
import { useBackNavigation } from '@/composables/useBackNavigation.js';
import ProductCard from "@/components/product/ProductCard.vue";
import SpecSelectDialog from "@/components/product/SpecSelectDialog.vue";

const cartStore = useCartStore();
const route = useRoute();
const router = useRouter();
const { goBack } = useBackNavigation();
const typeId = ref(route.params.id);
const isAllCategories = computed(() => typeId.value === "0");

const viewMode = ref("grid");
const sortBy = ref("default");
const sortOptions = [
  { label: "综合", value: "default" },
  { label: "销量优先", value: "sales" },
  { label: "价格从低到高", value: "price_asc" },
  { label: "价格从高到低", value: "price_desc" },
  { label: "最新上架", value: "newest" },
];

const searchForm = ref({
  fName: route.query.keyword || "",
  stockStatus: "",
  brand: undefined,
});

const brandOptions = ref([]);
const furnitureList = ref([]);
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(12);
const total = ref(0);
const typeInfo = ref({});

const showSidebar = ref(true);

const hasActiveFilters = computed(() => {
  return !!(
    searchForm.value.fName?.trim() ||
    searchForm.value.stockStatus ||
    searchForm.value.brand
  );
});

const specDialogVisible = ref(false);
const specData = ref({ specGroups: [], skuList: [] });
const specTarget = ref(null);

const setSort = (val) => {
  sortBy.value = val;
  currentPage.value = 1;
  loadFurnitureList();
};

const loadBrands = async () => {
  try {
    const res = await getFurnitureBrands(typeId.value);
    if (res.success || res.code === 200) {
      brandOptions.value = (res.data || [])
        .map((name) => ({ value: name, label: name }))
        .sort((a, b) => a.label.localeCompare(b.label, "zh-CN"));
    }
  } catch {
    brandOptions.value = [];
  }
};

const loadFurnitureList = async () => {
  loading.value = true;
  try {
    const params = { current: currentPage.value, size: pageSize.value };
    if (isAllCategories.value) {
      params.typeId = 0;
      if (searchForm.value.fName?.trim())
        params.keyword = searchForm.value.fName.trim();
    } else {
      params.typeId = typeId.value;
      if (searchForm.value.fName?.trim())
        params.fName = searchForm.value.fName.trim();
    }
    if (searchForm.value.stockStatus)
      params.stockStatus = searchForm.value.stockStatus;
    if (searchForm.value.brand) params.brand = searchForm.value.brand;
    if (sortBy.value === "sales") params.sortBy = "sales";
    else if (sortBy.value === "newest") params.sortBy = "newest";
    else if (sortBy.value === "price_asc") {
      params.sortBy = "price";
      params.sortOrder = "asc";
    } else if (sortBy.value === "price_desc") {
      params.sortBy = "price";
      params.sortOrder = "desc";
    }

    const res = await getFurnitureByTypeId(params);
    if ((res.success || res.code === 200) && res.data) {
      furnitureList.value = res.data.records || [];
      total.value = res.data.total || 0;
      if (furnitureList.value.length === 0 && currentPage.value > 1) {
        currentPage.value = 1;
        await loadFurnitureList();
      }
    } else {
      furnitureList.value = [];
      total.value = 0;
    }
  } catch (e) {
    logger.error("加载家具列表失败:", e);
    furnitureList.value = [];
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  loadFurnitureList();
};
const resetSearch = () => {
  searchForm.value = { fName: "", stockStatus: "", brand: undefined };
  handleSearch();
};
const resetSearchAndLoad = () => resetSearch();
const handleSizeChange = (v) => {
  pageSize.value = v;
  currentPage.value = 1;
  loadFurnitureList();
};
const handleCurrentChange = (v) => {
  currentPage.value = v;
  loadFurnitureList();
};

const loadTypeInfo = () => {
  if (isAllCategories.value) {
    typeInfo.value = { name: "全部商品", title: "搜索浏览所有家具" };
    return;
  }
  const cached = sessionStorage.getItem("currentType");
  if (cached) {
    const parsed = JSON.parse(cached);
    if (parsed.id == typeId.value) typeInfo.value = parsed;
  }
  if (!typeInfo.value.name) typeInfo.value = { name: "家具系列" };
};

const goToDetail = (item) =>
  router.push({ name: "FurnitureDetail", params: { id: item.id } });
const quickAdd = async (item) => {
  if (item.stock === 0) {
    ElMessage.warning("该商品已缺货");
    return;
  }
  try {
    const res = await getFurnitureSpecs(item.id);
    const groups = res.data?.specGroups || [];
    const skus = res.data?.skuList || [];

    if (groups.length > 0) {
      specTarget.value = item;
      specData.value = { specGroups: groups, skuList: skus };
      specDialogVisible.value = true;
    } else {
      const skuInfo = skus.length === 1
        ? {
            skuId: skus[0].id,
            price: skus[0].price,
            stock: skus[0].stock,
            skuImage: skus[0].skuImage,
            specText: skus[0].specText || '',
          }
        : null;
      cartStore.addItem(item, 1, skuInfo);
    }
  } catch {
    cartStore.addItem(item, 1);
  }
};

const onSpecConfirm = (skuInfo) => {
  if (specTarget.value) {
    cartStore.addItem(specTarget.value, 1, skuInfo);
    specTarget.value = null;
  }
  specDialogVisible.value = false;
};
const handleImgError = (e) => {
  e.target.style.display = "none";
};

watch(
  () => route.params.id,
  (newId) => {
    if (newId) {
      typeId.value = newId;
      const keyword = route.query.keyword || "";
      searchForm.value = { fName: keyword, stockStatus: "", brand: undefined };
      sortBy.value = "default";
      currentPage.value = 1;
      loadTypeInfo();
      loadBrands();
      loadFurnitureList();
    }
  },
);

onMounted(() => {
  loadTypeInfo();
  loadBrands();
  if (route.query.keyword) searchForm.value.fName = route.query.keyword;
  loadFurnitureList();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/type-detail-view.scss";
</style>
