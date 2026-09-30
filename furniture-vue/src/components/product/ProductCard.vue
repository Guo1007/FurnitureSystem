<template>
  <article class="product-card" @click="goDetail">
    <div class="card-img-wrap">
      <img
        loading="lazy"
        :src="imgUrl(product.fIcon)"
        :alt="product.fName"
        class="card-img"
        @error="handleImgError"
      />
      <div class="card-badges">
        <span class="badge badge-new" v-if="badgeLabel">{{ badgeLabel }}</span>
        <span
          class="badge badge-low"
          v-if="product.stock > 0 && product.stock < 10"
          >库存紧张</span
        >
        <span class="badge badge-out" v-if="product.stock === 0">暂时缺货</span>
      </div>
      <button
        class="quick-cart"
        @click.stop="quickAdd"
        :disabled="product.stock === 0"
        title="加入购物车"
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
        >
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
      </button>
    </div>
    <div class="card-info">
      <p class="card-brand" v-if="product.brand">{{ product.brand }}</p>
      <h3 class="card-name">{{ product.fName }}</h3>
      <div class="card-bottom">
        <span class="card-price">¥{{ formatPrice(product.price) }}</span>
        <span class="card-stock" v-if="product.stock > 0">有货</span>
      </div>
    </div>
  </article>

  <SpecSelectDialog
    v-model:visible="specDialogVisible"
    :product="product"
    :spec-groups="specData.specGroups"
    :sku-list="specData.skuList"
    @confirm="onSpecConfirm"
  />
</template>

<script setup>
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { imgUrl } from "@/utils/img.js";
import { formatPrice } from "@/utils/format.js";
import { useCartStore } from "@/stores/cart.js";
import { getFurnitureSpecs } from "@/api/furniture.js";
import SpecSelectDialog from "./SpecSelectDialog.vue";

const props = defineProps({
  product: { type: Object, required: true },
  badge: { type: String, default: "" },
});
const router = useRouter();
const cartStore = useCartStore();

const specDialogVisible = ref(false);
const specData = ref({ specGroups: [], skuList: [] });

const badgeLabel = computed(() => {
  if (props.badge === "hot") return "HOT";
  if (props.badge === "rec") return "RECOMMEND";
  if (props.badge === "new") return "NEW";
  return "";
});

const goDetail = () => {
  router.push({ name: "FurnitureDetail", params: { id: props.product.id } });
};

const quickAdd = async () => {
  if (props.product.stock === 0) {
    ElMessage.warning("该商品已缺货");
    return;
  }
  try {
    const res = await getFurnitureSpecs(props.product.id);
    const groups = res.data?.specGroups || [];
    const skus = res.data?.skuList || [];

    if (groups.length > 0) {
      // 有规格 — 弹出选择弹窗
      specData.value = { specGroups: groups, skuList: skus };
      specDialogVisible.value = true;
    } else {
      // 无规格 — 直接加入购物车（若只有一个 SKU 则自动带上）
      const skuInfo = skus.length === 1
        ? {
            skuId: skus[0].id,
            price: skus[0].price,
            stock: skus[0].stock,
            skuImage: skus[0].skuImage,
            specText: skus[0].specText || '',
          }
        : null;
      cartStore.addItem(props.product, 1, skuInfo);
    }
  } catch {
    // API 失败时 fallback 到原逻辑
    cartStore.addItem(props.product, 1);
  }
};

const onSpecConfirm = (skuInfo) => {
  cartStore.addItem(props.product, 1, skuInfo);
  specDialogVisible.value = false;
};

const handleImgError = (e) => {
  e.target.style.display = "none";
};
</script>

<style scoped lang="scss">
@import "@/styles/views/product-card.scss";
</style>
