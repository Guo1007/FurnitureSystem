<template>
  <el-dialog
    v-model="visible"
    :title="`规格管理 - ${furnitureName}`"
    width="900px"
    :close-on-click-modal="false"
    @close="onClose"
  >
    <el-tabs v-model="specActiveTab">
      <!-- 规格组管理 -->
      <el-tab-pane label="规格设置" name="spec">
        <div v-if="specGroups.length === 0" class="spec-empty-tip">
          <p>暂无规格，点击下方按钮添加规格组（如：颜色、尺寸等）</p>
        </div>
        <div
          v-for="(group, gIdx) in specGroups"
          :key="gIdx"
          class="spec-group-card"
        >
          <div class="spec-group-header">
            <el-input
              v-model="group.groupName"
              placeholder="规格组名称（如：颜色）"
              style="width: 200px"
              size="small"
            />
            <el-input-number
              v-model="group.sort"
              :min="0"
              size="small"
              style="width: 100px; margin-left: 10px"
              controls-position="right"
            />
            <el-button
              type="danger"
              text
              size="small"
              @click="removeSpecGroup(gIdx)"
              style="margin-left: auto"
              >删除该组
            </el-button>
          </div>
          <div class="spec-values-area">
            <div
              v-for="(val, vIdx) in group.values"
              :key="vIdx"
              class="spec-value-chip"
            >
              <el-input
                v-model="val.valueName"
                placeholder="规格值"
                size="small"
                style="width: 120px"
              />
              <el-button
                text
                size="small"
                @click="removeSpecValue(gIdx, vIdx)"
                >✕</el-button
              >
            </div>
            <el-button size="small" @click="addSpecValue(gIdx)"
              >+ 添加规格值</el-button
            >
          </div>
        </div>
        <el-button
          type="primary"
          plain
          @click="addSpecGroup"
          style="margin-top: 10px"
        >
          + 添加规格组
        </el-button>
      </el-tab-pane>

      <!-- SKU管理 -->
      <el-tab-pane label="SKU管理" name="sku">
        <div class="sku-actions">
          <el-button
            size="small"
            type="primary"
            @click="generateSkuTable"
            :disabled="specGroups.length === 0"
          >
            根据规格生成SKU
          </el-button>
          <el-button size="small" @click="addManualSku">手动添加SKU</el-button>
        </div>
        <el-table
          :data="skuTableData"
          border
          size="small"
          style="margin-top: 10px"
          max-height="400"
        >
          <el-table-column label="规格组合" min-width="180">
            <template #default="{ row, $index }">
              <span v-if="row._specText">{{ row._specText }}</span>
              <div
                v-else-if="specGroups.length > 0"
                class="manual-spec-selectors"
              >
                <el-select
                  v-for="g in specGroups.filter(
                    (grp) =>
                      grp.groupName && grp.values.some((v) => v.valueName),
                  )"
                  :key="g.id || g.groupName"
                  :model-value="(row._specValues || {})[g.groupName]"
                  @update:model-value="
                    (val) => onManualSpecChange($index, g.groupName, val)
                  "
                  :placeholder="'选择' + g.groupName"
                  size="small"
                  style="width: 110px; margin-bottom: 3px"
                >
                  <el-option
                    v-for="v in g.values.filter((v) => v.valueName)"
                    :key="v.id || v.valueName"
                    :label="v.valueName"
                    :value="v.valueName"
                  />
                </el-select>
              </div>
              <span v-else style="color: #999">无规格</span>
            </template>
          </el-table-column>
          <el-table-column label="价格(¥)" width="120">
            <template #default="{ row }">
              <el-input-number
                v-model="row.price"
                :min="0"
                :precision="2"
                size="small"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="库存" width="100">
            <template #default="{ row }">
              <el-input-number
                v-model="row.stock"
                :min="0"
                size="small"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="SKU图片" width="80">
            <template #default="{ row }">
              <el-upload
                class="sku-img-uploader"
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                :on-change="(file) => handleSkuImageChange(file, row)"
                accept="image/*"
              >
                <img
                  v-if="row.skuImage"
                  :src="imgUrl(row.skuImage)"
                  class="sku-img-thumb"
                />
                <el-icon v-else class="sku-img-add">
                  <Plus />
                </el-icon>
              </el-upload>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-switch
                v-model="row.status"
                :active-value="1"
                :inactive-value="0"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" fixed="right">
            <template #default="{ $index }">
              <el-button
                type="danger"
                text
                size="small"
                @click="removeSku($index)"
                >删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="sku-summary" v-if="skuTableData.length > 0">
          <span>共 {{ skuTableData.length }} 个SKU，</span>
          <span>总库存：{{ skuTotalStock }} 件，</span>
          <span>价格区间：{{ skuPriceRange }}</span>
        </div>
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSaveSpec" :loading="specSaving"
        >保存规格</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { getSpecAndSku, saveSpecAndSku } from "@/api/admin/spec.js";
import { uploadFurnitureImage } from "@/api/admin/furniture.js";
import { imgUrl } from "@/utils/img.js";
import { logger } from "@/utils/logger.js";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  furnitureId: { type: Number, default: null },
  furnitureName: { type: String, default: "" },
});

const emit = defineEmits(["update:modelValue", "saved"]);

// 可见性：v-model 双向绑定
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const specActiveTab = ref("spec");
const specSaving = ref(false);
const specGroups = ref([]);
const skuTableData = ref([]);

const skuTotalStock = computed(() =>
  skuTableData.value.reduce((sum, s) => sum + (s.stock || 0), 0),
);
const skuPriceRange = computed(() => {
  if (skuTableData.value.length === 0) return "-";
  const prices = skuTableData.value.map((s) => s.price).filter((p) => p > 0);
  if (prices.length === 0) return "-";
  const min = Math.min(...prices).toFixed(2);
  const max = Math.max(...prices).toFixed(2);
  return min === max ? `¥${min}` : `¥${min} ~ ¥${max}`;
});

// 打开弹窗时加载已有规格数据
watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      specActiveTab.value = "spec";
      specSaving.value = false;
      loadSpecData();
    }
  },
);

const loadSpecData = async () => {
  try {
    const res = await getSpecAndSku(props.furnitureId);
    if ((res.success || res.code === 200) && res.data) {
      const data = res.data;
      specGroups.value = (data.specGroups || []).map((g) => ({
        id: g.id,
        groupName: g.groupName,
        sort: g.sort || 0,
        values: (g.values || []).map((v) => ({
          id: v.id,
          valueName: v.valueName,
          valueImage: v.valueImage || "",
          sort: v.sort || 0,
        })),
      }));
      skuTableData.value = (data.skuList || []).map((s) => ({
        id: s.id,
        price: Number(s.price) || 0,
        stock: s.stock || 0,
        skuImage: s.skuImage || "",
        status: s.status != null ? s.status : 1,
        specValueIds: [],
        _specText: s.specText || "",
        _specs: [],
        _specValues: {},
      }));
      if (data.skuList) {
        data.skuList.forEach((s, idx) => {
          if (s.specMap && specGroups.value.length > 0) {
            const ids = [];
            const specs = [];
            specGroups.value.forEach((g) => {
              const valName = s.specMap[g.groupName];
              if (valName) {
                const matchVal = g.values.find((v) => v.valueName === valName);
                if (matchVal) ids.push(matchVal.id);
                specs.push({ groupName: g.groupName, valueName: valName });
              }
            });
            if (idx < skuTableData.value.length) {
              skuTableData.value[idx].specValueIds = ids;
              skuTableData.value[idx]._specs = specs;
            }
          }
        });
      }
    } else {
      specGroups.value = [];
      skuTableData.value = [];
    }
  } catch (e) {
    specGroups.value = [];
    skuTableData.value = [];
  }
};

const onClose = () => {
  specGroups.value = [];
  skuTableData.value = [];
};

const addSpecGroup = () => {
  specGroups.value.push({
    id: null,
    groupName: "",
    sort: specGroups.value.length,
    values: [{ id: null, valueName: "", valueImage: "", sort: 0 }],
  });
};

const removeSpecGroup = (gIdx) => {
  specGroups.value.splice(gIdx, 1);
};

const addSpecValue = (gIdx) => {
  const group = specGroups.value[gIdx];
  group.values.push({
    id: null,
    valueName: "",
    valueImage: "",
    sort: group.values.length,
  });
};

const removeSpecValue = (gIdx, vIdx) => {
  specGroups.value[gIdx].values.splice(vIdx, 1);
};

const generateSkuTable = () => {
  const validGroups = specGroups.value.filter(
    (g) => g.groupName && g.values.some((v) => v.valueName),
  );
  if (validGroups.length === 0) {
    ElMessage.warning("请先填写完整的规格组和规格值");
    return;
  }
  const combos = validGroups.reduce((acc, group) => {
    const validValues = group.values.filter((v) => v.valueName);
    if (acc.length === 0) return validValues.map((v) => [v]);
    const result = [];
    acc.forEach((combo) => {
      validValues.forEach((v) => {
        result.push([...combo, v]);
      });
    });
    return result;
  }, []);
  const oldSkuMap = {};
  skuTableData.value.forEach((s) => {
    const idsKey = (s.specValueIds || []).filter(Boolean).sort().join(",");
    if (idsKey) {
      oldSkuMap[idsKey] = s;
    } else if (s._specText) {
      oldSkuMap[s._specText] = s;
    }
  });
  skuTableData.value = combos.map((combo) => {
    const valueIds = combo.map((v) => v.id).sort();
    const idKey = valueIds.filter(Boolean).join(",");
    const specText = combo.map((v) => v.valueName).join(" / ");
    const existing = oldSkuMap[idKey] || oldSkuMap[specText];
    return {
      id: existing ? existing.id : null,
      price: existing ? existing.price : 0,
      stock: existing ? existing.stock : 0,
      skuImage: existing ? existing.skuImage : "",
      status: existing ? existing.status : 1,
      specValueIds: combo.map((v) => v.id),
      _specText: specText,
      _specs: combo.map((v, i) => ({
        groupName: validGroups[i].groupName,
        valueName: v.valueName,
      })),
    };
  });
  ElMessage.success(`已生成 ${combos.length} 个SKU组合`);
};

const addManualSku = () => {
  skuTableData.value.push({
    id: null,
    price: 0,
    stock: 0,
    skuImage: "",
    status: 1,
    specValueIds: [],
    _specText: "",
    _specs: [],
    _specValues: {},
  });
};

const removeSku = (idx) => {
  skuTableData.value.splice(idx, 1);
};

const onManualSpecChange = (rowIdx, groupName, valueName) => {
  const row = skuTableData.value[rowIdx];
  if (!row) return;
  if (!row._specValues) row._specValues = {};
  row._specValues = { ...row._specValues, [groupName]: valueName || undefined };
  const validGroups = specGroups.value.filter(
    (g) => g.groupName && g.values.some((v) => v.valueName),
  );
  const allSelected = validGroups.every((g) => row._specValues[g.groupName]);
  if (allSelected) {
    const specs = validGroups.map((g) => ({
      groupName: g.groupName,
      valueName: row._specValues[g.groupName],
    }));
    row._specText = specs.map((s) => s.valueName).join(" / ");
    row._specs = specs;
    row.specValueIds = [];
  } else {
    row._specText = "";
    row._specs = [];
  }
};

const handleSkuImageChange = async (file, row) => {
  if (!file) return;
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error("图片不能超过2MB");
    return;
  }
  try {
    const res = await uploadFurnitureImage(file.raw);
    if (res.success || res.code === 200) {
      row.skuImage = res.data;
    } else {
      ElMessage.error("上传失败");
    }
  } catch (e) {
    logger.error("上传出错:", e);
  }
};

const handleSaveSpec = async () => {
  for (let i = 0; i < specGroups.value.length; i++) {
    const g = specGroups.value[i];
    if (!g.groupName) {
      ElMessage.warning(`请填写第 ${i + 1} 个规格组的名称`);
      return;
    }
    const validVals = g.values.filter((v) => v.valueName);
    if (validVals.length === 0) {
      ElMessage.warning(`规格组 "${g.groupName}" 至少需要一个规格值`);
      return;
    }
  }
  for (let i = 0; i < skuTableData.value.length; i++) {
    const s = skuTableData.value[i];
    if (!s.price || s.price <= 0) {
      ElMessage.warning(`第 ${i + 1} 个SKU的价格必须大于0`);
      return;
    }
  }
  specSaving.value = true;
  try {
    const dto = {
      furnitureId: props.furnitureId,
      specGroups: specGroups.value.map((g) => ({
        id: g.id,
        groupName: g.groupName,
        sort: g.sort,
        values: g.values
          .filter((v) => v.valueName)
          .map((v) => ({
            id: v.id,
            valueName: v.valueName,
            valueImage: v.valueImage,
            sort: v.sort,
          })),
      })),
      skuList: skuTableData.value.map((s) => ({
        id: s.id,
        price: s.price,
        stock: s.stock,
        skuImage: s.skuImage,
        status: s.status,
        specValueIds: s.specValueIds,
        specs: s._specs || [],
      })),
    };
    const res = await saveSpecAndSku(dto);
    if (res.success || res.code === 200) {
      ElMessage.success("规格保存成功");
      visible.value = false;
      emit("saved", props.furnitureId);
    } else {
      ElMessage.error(res.msg || "保存失败");
    }
  } catch (e) {
    logger.error("保存异常:", e);
  } finally {
    specSaving.value = false;
  }
};
</script>

<style scoped lang="scss">
@import "@/styles/views/spec-manage-dialog.scss";
</style>