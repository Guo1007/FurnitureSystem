<template>
  <div class="manage-page">
    <h2 class="page-title">家具管理</h2>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-select
        v-model="searchForm.typeId"
        placeholder="分类"
        clearable
        style="width: 150px"
      >
        <el-option
          v-for="type in typeList"
          :key="type.id"
          :label="type.name"
          :value="type.id"
        />
      </el-select>
      <el-input
        v-model="searchForm.fName"
        placeholder="家具名称"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
      />
      <el-select
        v-model="searchForm.stockStatus"
        placeholder="库存状态"
        clearable
        style="width: 150px"
      >
        <el-option label="有库存" value="in_stock" />
        <el-option label="库存紧张" value="low_stock" />
        <el-option label="无库存" value="out_stock" />
      </el-select>
      <el-input
        v-model="searchForm.brand"
        placeholder="品牌"
        clearable
        style="width: 150px"
      />
      <el-button type="primary" @click="handleSearch"
        >搜索</el-button
      >
      <el-button @click="resetSearch">重置</el-button>
      <el-button type="success" style="margin-left: auto" @click="handleAdd"
        >+ 新增家具</el-button
      >
    </div>

    <!-- 表格 -->
    <el-table :data="furnitureList" v-loading="loading" border>
      <el-table-column label="图片" width="80">
        <template #default="{ row }">
          <img
            v-if="row.fIcon"
            :src="imgUrl(row.fIcon)"
            class="table-img"
            @error="$event.target.style.display = 'none'"
          />
          <span v-else>🪑</span>
        </template>
      </el-table-column>
      <el-table-column prop="fName" label="家具名称" min-width="150" />
      <el-table-column prop="brand" label="品牌" width="120" />
      <el-table-column prop="price" label="价格" width="120">
        <template #default="{ row }"> ¥{{ row.price }} </template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="100">
        <template #default="{ row }">
          <el-tag :type="getStockType(row.stock)">
            {{ row.stock }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="typeId" label="分类" width="100">
        <template #default="{ row }">
          {{ getTypeName(row.typeId) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button type="success" size="small" @click="openSpecDialog(row)"
            >规格</el-button
          >
          <el-button type="danger" size="small" @click="handleDelete(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="loadData"
      />
    </div>

    <!-- 新增/编辑 弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑家具' : '新增家具'"
      width="600px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <!-- 隐藏 ID，仅编辑时使用 -->
        <!-- <el-form-item label="ID" v-if="isEdit"> -->
        <!-- <el-input v-model="form.id" disabled/> -->
        <!-- </el-form-item> -->

        <el-form-item label="名称" prop="fName">
          <el-input v-model="form.fName" placeholder="请输入家具名称" />
        </el-form-item>

        <!-- 图片上传改为组件 -->
        <el-form-item label="图片" prop="fIcon">
          <div class="upload-container">
            <el-upload
              class="avatar-uploader"
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleImageChange"
              accept="image/*"
            >
              <img v-if="form.fIcon" :src="imgUrl(form.fIcon)" class="avatar" />
              <el-icon v-else class="avatar-uploader-icon">
                <Plus />
              </el-icon>
            </el-upload>
            <div class="upload-tip">
              <el-button v-if="uploading" type="primary" loading
                >上传中...</el-button
              >
              <span v-else-if="form.fIcon" class="tip-text">点击更换图片</span>
              <span v-else class="tip-text">点击上传图片</span>
            </div>
          </div>
        </el-form-item>

        <!-- 多图片上传 -->
        <el-form-item label="详情图">
          <div class="images-upload-area">
            <div
              class="image-thumb"
              v-for="(img, idx) in imagesList"
              :key="idx"
            >
              <img
                :src="imgUrl(img)"
                class="thumb-preview"
                @error="(e) => (e.target.style.display = 'none')"
              />
              <span class="thumb-delete" @click="removeImage(idx)">✕</span>
            </div>
            <div
              class="image-add-btn"
              @click="triggerImageUpload"
              v-if="imagesList.length < 8"
            >
              <el-icon :size="28">
                <Plus />
              </el-icon>
              <span v-if="uploadingImages" style="font-size: 11px">上传中</span>
            </div>
            <input
              type="file"
              accept="image/*"
              ref="multiImageInput"
              style="display: none"
              @change="onMultiImageChange"
            />
          </div>
          <div class="form-item-tip" style="margin-top: 6px">
            可上传最多8张详情图片，用于商品详情页展示
          </div>
        </el-form-item>

        <el-form-item label="分类" prop="typeId">
          <el-select
            v-model="form.typeId"
            placeholder="请选择分类"
            style="width: 100%"
          >
            <el-option
              v-for="type in typeList"
              :key="type.id"
              :label="type.name"
              :value="type.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="价格" prop="price">
          <el-input-number
            v-model="form.price"
            :min="0"
            :precision="2"
            style="width: 100%"
            :disabled="isEdit && hasSku"
          />
          <div
            v-if="isEdit && hasSku"
            class="form-item-tip"
            style="color: #e6a23c; margin-top: 4px"
          >
            ⚠️ 该商品已配置规格，价格由规格最低价自动同步
          </div>
        </el-form-item>

        <el-form-item label="品牌" prop="brand">
          <el-input v-model="form.brand" placeholder="请输入品牌" />
        </el-form-item>

        <el-form-item label="库存" prop="stock">
          <el-input-number
            v-model="form.stock"
            :min="0"
            style="width: 100%"
            :disabled="isEdit && hasSku"
          />
          <div
            v-if="isEdit && hasSku"
            class="form-item-tip"
            style="color: #e6a23c; margin-top: 4px"
          >
            ⚠️ 该商品已配置规格，库存由各规格库存自动汇总，不可手动修改
          </div>
        </el-form-item>

        <el-form-item label="简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="3"
            placeholder="请输入家具简介"
          />
        </el-form-item>

        <el-form-item label="编辑推荐">
          <el-switch
            v-model="form.isRecommended"
            :active-value="1"
            :inactive-value="0"
          />
          <span
            style="
              margin-left: 8px;
              font-size: 13px;
              color: var(--color-text-tertiary);
            "
          >
            {{ form.isRecommended === 1 ? '已在首页"编辑推荐"展示' : "关闭" }}
          </span>
        </el-form-item>

        <el-form-item label="商品描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="5"
            placeholder="请输入商品详细描述（支持多行）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 规格管理弹窗（独立子组件） -->
    <SpecManageDialog
      v-model="specDialogVisible"
      :furniture-id="specFurnitureId"
      :furniture-name="specFurnitureName"
      @saved="loadData"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import {
  addFurniture,
  deleteFurniture,
  editFurniture,
  getFurnitureList,
  uploadFurnitureImage,
} from "@/api/admin/furniture.js";
import { getFurnitureTypeList } from "@/api/furniture.js";
import SpecManageDialog from "@/components/admin/SpecManageDialog.vue";
import { imgUrl } from "@/utils/img.js";
import { logger } from "@/utils/logger.js";

const loading = ref(false);
const submitLoading = ref(false);
const uploading = ref(false);
const uploadingImages = ref(false);
const furnitureList = ref([]);
const typeList = ref([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const dialogVisible = ref(false);
const isEdit = ref(false);
const formRef = ref(null);
const multiImageInput = ref(null);

// 多图片列表（comma-separated ↔ array）
const imagesList = computed(() => {
  if (!form.images) return [];
  return form.images
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
});

// 搜索表单
const searchForm = ref({
  typeId: null,
  fName: "",
  stockStatus: "",
  brand: "",
});

// 编辑/新增表单数据
const form = reactive({
  id: null,
  fName: "",
  fIcon: "",
  images: "",
  typeId: null,
  price: 0,
  brand: "",
  stock: 0,
  intro: "",
  description: "",
  isRecommended: 0,
});

// ========== 规格管理（子组件控制） ==========
const specDialogVisible = ref(false);
const specFurnitureId = ref(null);
const specFurnitureName = ref("");
const hasSku = ref(false);

// 表单校验规则
const rules = {
  fName: [{ required: true, message: "请输入家具名称", trigger: "blur" }],
  fIcon: [{ required: true, message: "请上传图片", trigger: "blur" }],
  typeId: [{ required: true, message: "请选择分类", trigger: "change" }],
  price: [{ required: true, message: "请输入价格", trigger: "blur" }],
  brand: [{ required: true, message: "请输入品牌", trigger: "blur" }],
  stock: [{ required: true, message: "请输入库存", trigger: "blur" }],
};

// --- 方法 ---

// 加载分类
const loadTypes = async () => {
  const res = await getFurnitureTypeList();
  if (res.success || res.code === 200) {
    typeList.value = res.data || [];
  }
};

// 加载列表
const loadData = async () => {
  loading.value = true;
  try {
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      typeId: searchForm.value.typeId,
      fName: searchForm.value.fName || undefined,
      stockStatus: searchForm.value.stockStatus || undefined,
      brand: searchForm.value.brand || undefined,
    };
    Object.keys(params).forEach((key) => {
      if (params[key] === null || params[key] === "") delete params[key];
    });

    const res = await getFurnitureList(params);
    if (res.success || res.code === 200) {
      furnitureList.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (error) {
    logger.error("加载失败:", error);
  } finally {
    loading.value = false;
  }
};

// 图片上传处理 - 参考 useProfile.js 的 handleAvatarUpload
const handleImageChange = async (file) => {
  if (!file) return;

  // 文件大小校验（2MB）
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error("图片不能超过2MB");
    return false;
  }

  uploading.value = true;
  try {
    const res = await uploadFurnitureImage(file.raw);
    if (res.success || res.code === 200) {
      const imageUrl = res.data;
      form.fIcon = imageUrl;
      ElMessage.success("图片上传成功");
      return true;
    } else {
      ElMessage.error(res.msg || "上传失败");
      return false;
    }
  } catch (error) {
    logger.error("上传出错:", error);
    return false;
  } finally {
    uploading.value = false;
  }
};

// 多图片上传
const triggerImageUpload = () => {
  multiImageInput.value?.click();
};

const onMultiImageChange = async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error("图片不能超过2MB");
    e.target.value = "";
    return;
  }
  uploadingImages.value = true;
  try {
    const res = await uploadFurnitureImage(file);
    if (res.success || res.code === 200) {
      const url = res.data;
      const list = [...imagesList.value, url];
      form.images = list.join(",");
    } else {
      ElMessage.error(res.msg || "上传失败");
    }
  } catch (err) {
    logger.error("上传出错:", err);
  } finally {
    uploadingImages.value = false;
    e.target.value = "";
  }
};

const removeImage = (idx) => {
  const list = [...imagesList.value];
  list.splice(idx, 1);
  form.images = list.join(",");
};

// 搜索与重置
const handleSearch = () => {
  currentPage.value = 1;
  loadData();
};
const resetSearch = () => {
  searchForm.value = { typeId: null, fName: "", stockStatus: "", brand: "" };
  handleSearch();
};

// 打开新增弹窗
const handleAdd = () => {
  isEdit.value = false;
  hasSku.value = false;
  Object.assign(form, {
    id: null,
    fName: "",
    fIcon: "",
    images: "",
    typeId: null,
    price: 0,
    brand: "",
    stock: 0,
    intro: "",
    description: "",
  });
  dialogVisible.value = true;
};

// 打开编辑弹窗
const handleEdit = async (row) => {
  isEdit.value = true;
  Object.assign(form, row);
  form.isRecommended = row.isRecommended ?? 0;
  // 检查是否有规格，有规格时库存由规格自动汇总
  try {
    const { getSpecAndSku } = await import("@/api/admin/spec.js");
    const res = await getSpecAndSku(row.id);
    if ((res.success || res.code === 200) && res.data) {
      hasSku.value = (res.data.skuList || []).length > 0;
    } else {
      hasSku.value = false;
    }
  } catch {
    hasSku.value = false;
  }
  dialogVisible.value = true;
};

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return;

  // 库存为 0 时二次确认（有规格时跳过，库存由规格自动汇总）
  if (form.stock === 0 && !(isEdit.value && hasSku.value)) {
    try {
      await ElMessageBox.confirm(
        "库存设置为 0 意味着该商品暂不可售，确认继续？",
        "库存确认",
        {
          confirmButtonText: "确认设置为 0",
          cancelButtonText: "取消",
          type: "warning",
        },
      );
    } catch {
      return;
    }
  }

  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true;
      try {
        const res = isEdit.value
          ? await editFurniture(form)
          : await addFurniture(form);

        if (res.success || res.code === 200) {
          ElMessage.success(isEdit.value ? "修改成功" : "添加成功");
          dialogVisible.value = false;
          loadData();
        } else {
          ElMessage.error(res.msg || "操作失败");
        }
      } catch (error) {
        logger.error("操作异常:", error);
      } finally {
        submitLoading.value = false;
      }
    }
  });
};

// 删除
const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除家具 "${row.fName}" 吗？`, "提示", {
    type: "warning",
  })
    .then(async () => {
      try {
        const res = await deleteFurniture(row.id);
        if (res.success || res.code === 200) {
          ElMessage.success("删除成功");
          loadData();
        } else {
          ElMessage.error(res.msg || "删除失败");
        }
      } catch (error) {
        logger.error("删除异常:", error);
      }
    })
    .catch(() => {});
};

const getStockType = (stock) => {
  if (stock === 0) return "danger";
  if (stock < 10) return "warning";
  return "success";
};

const getTypeName = (typeId) => {
  const type = typeList.value.find((t) => t.id === typeId);
  return type?.name || "-";
};

const handleSizeChange = (val) => {
  currentPage.value = 1;
  loadData();
};

// 打开规格管理弹窗（数据加载由子组件负责）
const openSpecDialog = (row) => {
  specFurnitureId.value = row.id;
  specFurnitureName.value = row.fName;
  specDialogVisible.value = true;
};

onMounted(() => {
  loadTypes();
  loadData();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/furniture-manage.scss";
</style>