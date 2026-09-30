<template>
  <div class="favorites-container-new">
    <div class="page-breadcrumb">
      <button class="breadcrumb-back" @click="goBack" title="返回">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
      </button>
      <router-link to="/">首页</router-link>
      <span>/</span>
      <span class="current">我的收藏</span>
      <span class="fav-count-bread" v-if="total > 0">（{{ total }} 件）</span>
    </div>
    <main class="main-content">
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <p>加载收藏列表...</p>
      </div>
      <div v-else-if="list.length === 0" class="empty-state">
        <span class="empty-icon">💝</span>
        <p>还没有收藏任何家具</p>
        <el-button type="primary" @click="goHome">去逛逛</el-button>
      </div>
      <template v-else>
        <div class="fav-grid">
          <div
            class="fav-card"
            v-for="item in list"
            :key="item.id"
            @click="goDetail(item)"
          >
            <img
              loading="lazy"
              :src="imgUrl(item.fIcon, '/images/default-furniture.png')"
              class="fav-img"
              @error="handleImgError"
            />
            <div class="fav-info">
              <h3>{{ item.fName }}</h3>
              <p class="fav-price">¥{{ formatPrice(item.price) }}</p>
            </div>
            <el-button
              type="danger"
              text
              size="small"
              @click.stop="handleRemove(item)"
              class="remove-btn"
            >
              取消收藏
            </el-button>
          </div>
        </div>
        <div class="pagination-wrapper">
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
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getFavorites, toggleFavorite } from "@/api/favorite.js";
import { imgUrl } from "@/utils/img.js";
import { formatPrice } from "@/utils/format.js";
import { logger } from "@/utils/logger.js";
import { useBackNavigation } from '@/composables/useBackNavigation.js';

const router = useRouter();
const list = ref([]);
const loading = ref(true);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);

const loadList = async () => {
  loading.value = true;
  try {
    const res = await getFavorites(currentPage.value, pageSize.value);
    if ((res.success || res.code === 200) && res.data) {
      if (res.data.records) {
        list.value = res.data.records;
        total.value = res.data.total || 0;
      } else if (Array.isArray(res.data)) {
        list.value = res.data;
        total.value = res.data.length;
      }
    }
  } catch (e) {
    logger.error("加载收藏失败:", e);
  } finally {
    loading.value = false;
  }
};

const handleSizeChange = () => {
  currentPage.value = 1;
  loadList();
};

const handleCurrentChange = () => {
  loadList();
};

const handleRemove = async (item) => {
  try {
    const res = await toggleFavorite(item.id);
    if (res.success || res.code === 200) {
      ElMessage.success("已取消收藏");
      loadList();
    } else {
      ElMessage.error(res.msg || "取消收藏失败");
    }
  } catch (e) {
    logger.error("handleRemove:", e);
  }
};

const goDetail = (item) =>
  router.push({ name: "FurnitureDetail", params: { id: item.id } });
const goHome = () => router.push("/");
const { goBack } = useBackNavigation();

const handleImgError = (e) => {
  e.target.src = "/images/default-furniture.png";
};

onMounted(() => loadList());
</script>

<style scoped lang="scss">
@import "@/styles/views/user-favorites-view.scss";
</style>
