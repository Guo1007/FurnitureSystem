<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <!-- Brand column -->
      <div class="footer-brand">
        <div class="footer-logo">
          <img
            loading="lazy"
            v-if="sys.systemLogo"
            :src="imgUrl(sys.systemLogo)"
            class="footer-logo-img"
            alt=""
          />
          <span v-else class="footer-logo-mark">木</span>
          <span class="footer-logo-name">{{ sys.systemName }}</span>
        </div>
        <p class="footer-desc">
          精选优质家居好物，打造理想生活空间。<br />
          官方授权 · 正品保障 · 极速发货。
        </p>
        <div class="footer-contact">
          <div class="contact-item">
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path
                d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"
              />
            </svg>
            <span>{{ sys.contactInfo.phone }}</span>
          </div>
          <div class="contact-item">
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path
                d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"
              />
              <polyline points="22,6 12,13 2,6" />
            </svg>
            <span>{{ sys.contactInfo.email }}</span>
          </div>
        </div>
      </div>

      <!-- Link columns -->
      <div class="footer-links">
        <div class="link-group">
          <h4>购物指南</h4>
          <router-link to="/type/0">全部商品</router-link>
          <router-link to="/user/orders">我的订单</router-link>
        </div>
        <div class="link-group">
          <h4>客户服务</h4>
          <router-link to="/user/addresses">收货地址</router-link>
          <router-link to="/user/favorites">我的收藏</router-link>
          <router-link to="/user/profile">个人中心</router-link>
        </div>
        <div class="link-group">
          <h4>关于我们</h4>
          <router-link to="/about">平台介绍</router-link>
          <router-link to="/about#contact">客服与反馈</router-link>
        </div>
      </div>
    </div>

    <div class="footer-bottom">
      <div class="footer-bottom-inner">
        <span>&copy; 2026 {{ sys.systemName }}. All rights reserved.</span>
        <span class="footer-icp">精选家具 · 品质生活</span>
      </div>
    </div>
  </footer>
</template>

<script setup>
import { onMounted } from "vue";
import { useSystemStore } from "@/stores/system.js";
import { imgUrl } from "@/utils/img.js";

const sys = useSystemStore();
onMounted(() => sys.load());
</script>

<style scoped lang="scss">
@import "@/styles/views/app-footer.scss";
</style>
