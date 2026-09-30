<template>
  <div class="home-page">
    <!-- Hero Carousel -->
    <HeroBanner />

    <!-- Service Bar -->
    <ServiceBar />

    <!-- Categories -->
    <section class="categories-section">
      <div class="section-hd">
        <div>
          <h2 class="section-title">{{ catTitle }}</h2>
          <p class="section-sub">{{ catSub }}</p>
        </div>
      </div>

      <div v-if="catLoading" class="cat-skeleton">
        <div class="cat-sk-item" v-for="i in 6" :key="i">
          <div class="cat-sk-icon"></div>
          <div class="cat-sk-line"></div>
        </div>
      </div>

      <div class="cat-grid" v-else-if="categories.length > 0">
        <router-link
          v-for="cat in categories"
          :key="cat.id"
          :to="`/type/${cat.id}`"
          class="cat-card"
          @click="saveTypeInfo(cat)"
        >
          <div class="cat-icon-box">
            <span v-if="cat.icon && !isImgUrl(cat.icon)" class="cat-emoji">{{
              cat.icon
            }}</span>
            <img
              loading="lazy"
              v-else-if="cat.icon"
              :src="imgUrl(cat.icon)"
              alt=""
              class="cat-icon-img"
            />
            <span v-else class="cat-emoji">🪑</span>
          </div>
          <h3 class="cat-name">{{ cat.name }}</h3>
          <p class="cat-desc">{{ cat.title || cat.name + "系列家具" }}</p>
        </router-link>
      </div>

      <div v-else class="empty-state">
        <p>暂无分类数据</p>
      </div>
    </section>

    <!-- Product Tabs -->
    <ProductTabs />

    <!-- Scene Guide Section -->
    <section class="scene-section">
      <div class="scene-inner">
        <div class="scene-hd">
          <p class="scene-label">按场景选购</p>
          <h2 class="scene-title">按场景选家具 · 省心又好看</h2>
          <p class="scene-sub">精选 3 大生活场景套装，一键直达对应分类，搭配更划算</p>
        </div>
        <div class="scene-grid">
          <router-link
            v-for="s in sceneList"
            :key="s.key"
            :to="`/type/${s.typeId}`"
            class="scene-card"
            @click="saveTypeInfo(s.category)"
          >
            <div class="scene-icon">{{ s.icon }}</div>
            <h3 class="scene-card-title">{{ s.label }}</h3>
            <p class="scene-card-desc">{{ s.desc }}</p>
            <div class="scene-card-tag">{{ s.highlight }}</div>
            <div class="scene-card-cta">
              去看看
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
            </div>
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { getFurnitureTypeList } from "@/api/furniture.js";
import { imgUrl } from "@/utils/img.js";
import { useSystemStore } from "@/stores/system.js";
import HeroBanner from "@/components/home/HeroBanner.vue";
import ServiceBar from "@/components/home/ServiceBar.vue";
import ProductTabs from "@/components/home/ProductTabs.vue";

const sys = useSystemStore();
const categories = ref([]);
const catLoading = ref(true);
const catTitle = ref("家具分类");
const catSub = ref("选择你感兴趣的品类");

const isImgUrl = (str) =>
  str && (str.startsWith("/") || str.startsWith("http"));

const saveTypeInfo = (cat) => {
  if (!cat) return;
  sessionStorage.setItem(
    "currentType",
    JSON.stringify({
      id: cat.id,
      name: cat.name,
      icon: cat.icon,
      title: cat.title,
    }),
  );
};

// 场景导购预设（通过 matchTypeName 与分类接口返回做匹配）
const SCENE_PRESETS = [
  {
    key: "living",
    label: "客厅焕新",
    icon: "🏠",
    desc: "沙发 · 茶几 · 鞋柜一站配齐",
    matchTypeName: "门厅系列",
    highlight: "热销组合",
  },
  {
    key: "bedroom",
    label: "卧室套装",
    icon: "🛏️",
    desc: "床垫 · 衣柜 · 床头柜",
    matchTypeName: "卧室系列",
    highlight: "焕新睡眠",
  },
  {
    key: "study",
    label: "书房配齐",
    icon: "📚",
    desc: "书桌 · 书架组合",
    matchTypeName: "书房系列",
    highlight: "高效办公",
  },
];

const sceneList = computed(() => {
  return SCENE_PRESETS.map((preset) => {
    const cat = categories.value.find((c) => c.name === preset.matchTypeName);
    return { ...preset, typeId: cat ? cat.id : 0, category: cat };
  }).filter((s) => s.typeId > 0);
});

const loadSiteLabels = async () => {
  try {
    await sys.load();
    const labels = sys.siteData?.label || [];
    const cat = labels.find((l) => l.sectionKey === "home_categories");
    if (cat) {
      catTitle.value = cat.contentTitle;
      if (cat.contentText) catSub.value = cat.contentText;
    }
  } catch {
    /* ignore */
  }
};

const loadCategories = async () => {
  catLoading.value = true;
  try {
    const res = await getFurnitureTypeList();
    if (res.success && Array.isArray(res.data)) categories.value = res.data;
  } catch {
    categories.value = [];
  } finally {
    catLoading.value = false;
  }
};

onMounted(async () => {
  loadCategories();
  loadSiteLabels();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/home-view.scss";
</style>
