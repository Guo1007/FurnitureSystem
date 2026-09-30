<template>
  <el-dialog
    v-model="visible"
    title="选择规格"
    width="420px"
    :close-on-click-modal="false"
    destroy-on-close
    class="spec-dialog"
  >
    <div class="dialog-body">
      <!-- 商品摘要 -->
      <div class="product-summary">
        <div class="ps-img-wrap">
          <img
            loading="lazy"
            v-if="product.fIcon"
            :src="imgUrl(product.fIcon)"
            :alt="product.fName"
            class="ps-img"
          />
          <span v-else class="ps-emoji">🪑</span>
        </div>
        <div class="ps-info">
          <p class="ps-name">{{ product.fName }}</p>
          <p class="ps-price">¥{{ formatPrice(displayPrice) }}</p>
        </div>
      </div>

      <!-- 规格选择 -->
      <div class="spec-section" v-if="specGroups.length > 0">
        <div class="spec-group" v-for="group in specGroups" :key="group.id">
          <div class="spec-group-label">{{ group.groupName }}</div>
          <div class="spec-values">
            <div
              v-for="val in group.values"
              :key="val.id"
              class="spec-value-item"
              :class="{
                active: selectedSpecs[group.groupName] === val.valueName,
                disabled: !isSpecValueAvailable(skuList, group.groupName, val.valueName),
              }"
              @click="onSelectSpec(group.groupName, val.valueName)"
            >
              <span>{{ val.valueName }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 已选规格提示 -->
      <div class="spec-selected-info" v-if="selectedSku">
        已选：{{ selectedSku.specText }}
      </div>

      <!-- 库存提示 -->
      <div class="stock-info" v-if="specGroups.length > 0 && !selectedSku">
        <span class="stock-hint">请选择完整规格</span>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button
        type="primary"
        :disabled="specGroups.length > 0 && !selectedSku"
        @click="handleConfirm"
      >
        加入购物车
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, watch } from 'vue'
import { imgUrl } from '@/utils/img.js'
import { formatPrice } from '@/utils/format.js'
import { useSpecSelection } from '@/composables/useSpecSelection.js'

const props = defineProps({
  visible: Boolean,
  product: { type: Object, default: () => ({}) },
  specGroups: { type: Array, default: () => [] },
  skuList: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:visible', 'confirm'])

const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const {
  selectedSpecs,
  selectedSku,
  selectSpec,
  isSpecValueAvailable,
  resetSelection,
} = useSpecSelection()

// 当弹窗打开时重置选择状态
watch(() => props.visible, (val) => {
  if (val) resetSelection()
})

// 当前显示价格（选中 SKU 时用 SKU 价格）
const displayPrice = computed(() => {
  if (selectedSku.value) return selectedSku.value.price
  return props.product.price || 0
})

const onSelectSpec = (groupName, valueName) => {
  selectSpec(props.specGroups, props.skuList, groupName, valueName)
}

const handleCancel = () => {
  visible.value = false
}

const handleConfirm = () => {
  const sku = selectedSku.value
  if (!sku) return
  emit('confirm', {
    skuId: sku.id,
    price: sku.price,
    stock: sku.stock,
    skuImage: sku.skuImage,
    specText: sku.specText || '',
  })
}
</script>

<style scoped lang="scss">
@import "@/styles/views/spec-select-dialog.scss";
</style>
