<template>
  <section class="hero">
    <!-- 骨架屏 -->
    <div v-if="loading" class="hero-skeleton">
      <div class="hero-sk-inner">
        <div class="hero-sk-text">
          <div class="hero-sk-tag"></div>
          <div class="hero-sk-title"></div>
          <div class="hero-sk-desc"></div>
          <div class="hero-sk-btn"></div>
        </div>
        <div class="hero-sk-visual">
          <div class="hero-sk-circle"></div>
        </div>
      </div>
    </div>

    <div
      v-else
      class="hero-track"
      :style="{ transform: `translateX(-${current * 100}%)` }"
    >
      <div class="hero-slide" v-for="(slide, i) in slides" :key="i">
        <div class="hero-bg" :style="{ backgroundColor: slide.bg }"></div>
        <div class="hero-inner">
          <div class="hero-text">
            <p class="hero-tag">{{ slide.tag }}</p>
            <h2 class="hero-title">{{ slide.title }}</h2>
            <p class="hero-desc">{{ slide.desc }}</p>
            <router-link :to="slide.link" class="hero-cta">
              {{ slide.cta }}
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </router-link>
          </div>
          <div class="hero-visual">
            <div class="hero-visual-inner">
              <img
                loading="lazy"
                v-if="slide.image"
                :src="imgUrl(slide.image)"
                class="hero-image"
                alt=""
              />
              <span v-else class="hero-emoji">{{ slide.emoji || "🛋️" }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Controls -->
    <button class="hero-arrow hero-prev" @click="prev" aria-label="上一张">
      <svg
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
      >
        <path d="M15 18l-6-6 6-6" />
      </svg>
    </button>
    <button class="hero-arrow hero-next" @click="next" aria-label="下一张">
      <svg
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
      >
        <path d="M9 18l6-6-6-6" />
      </svg>
    </button>

    <!-- Dots -->
    <div class="hero-dots">
      <button
        v-for="(s, i) in slides"
        :key="i"
        :class="{ active: i === current }"
        @click="current = i"
      ></button>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useSystemStore } from "@/stores/system.js";
import { imgUrl } from "@/utils/img.js";

const sys = useSystemStore();
const current = ref(0);
const slides = ref([]);
const loading = ref(true);

const loadSlides = async () => {
  try {
    await sys.load();
    if (sys.siteData?.carousel) {
      slides.value = sys.siteData.carousel.map((s) => {
        const extra = parseExtra(s.extraData);
        return {
          bg: extra.bg || "#e8e0d5",
          tag: extra.tag || "",
          title: s.contentTitle || "",
          desc: s.contentText || "",
          cta: extra.cta || "了解更多",
          link: s.linkUrl,
          emoji: extra.emoji || "",
          image: s.imageUrl || "",
        };
      });
    }
  } catch {
    /* keep defaults */
  } finally {
    loading.value = false;
  }
};

const parseExtra = (str) => {
  try {
    return JSON.parse(str) || {};
  } catch {
    return {};
  }
};

let timer = null;
const startTimer = () => {
  if (slides.value.length > 0)
    timer = setInterval(() => {
      current.value = (current.value + 1) % slides.value.length;
    }, 5000);
};
const stopTimer = () => {
  clearInterval(timer);
};
const prev = () => {
  stopTimer();
  current.value =
    (current.value - 1 + slides.value.length) % slides.value.length;
  startTimer();
};
const next = () => {
  stopTimer();
  current.value = (current.value + 1) % slides.value.length;
  startTimer();
};

onMounted(async () => {
  await loadSlides();
  startTimer();
});
onBeforeUnmount(stopTimer);
</script>

<style scoped lang="scss">
@import "@/styles/views/hero-banner.scss";
</style>
