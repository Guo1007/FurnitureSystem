<template>
  <div class="manage-page">
    <h2 class="page-title">家具类型管理</h2>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchForm.name"
        placeholder="类型名称"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch"
        >搜索</el-button
      >
      <el-button @click="resetSearch">重置</el-button>
      <el-button type="success" style="margin-left: auto" @click="handleAdd"
        >+ 新增类型</el-button
      >
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="图标" width="80">
        <template #default="{ row }">
          <img
            v-if="row.icon"
            :src="imgUrl(row.icon)"
            class="table-img"
            @error="$event.target.style.display = 'none'"
          />
          <span v-else>🏷️</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="类型名称" min-width="150" />
      <el-table-column
        prop="title"
        label="描述"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="handleEdit(row.id)"
            >编辑</el-button
          >
          <el-button type="danger" size="small" @click="handleDelete(row.id)"
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
        @current-change="loadList"
      />
    </div>

    <!-- 新增/编辑 弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="ID" prop="id" v-if="formData.id">
          <el-input v-model="formData.id" disabled />
        </el-form-item>
        <el-form-item label="类型名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入类型名称" />
        </el-form-item>
        <el-form-item label="描述" prop="title">
          <el-input v-model="formData.title" placeholder="请输入描述" />
        </el-form-item>

        <!-- 图标上传改为组件 -->
        <el-form-item label="图标" prop="icon">
          <div class="upload-container">
            <el-upload
              class="avatar-uploader"
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleIconChange"
              accept="image/*"
            >
              <img
                v-if="formData.icon"
                :src="imgUrl(formData.icon)"
                class="avatar"
              />
              <el-icon v-else class="avatar-uploader-icon">
                <Plus />
              </el-icon>
            </el-upload>
            <div class="upload-tip">
              <el-button v-if="uploading" type="primary" loading
                >上传中...</el-button
              >
              <span v-else-if="formData.icon" class="tip-text"
                >点击更换图标</span
              >
              <span v-else class="tip-text">点击上传图标</span>
            </div>
          </div>
          <el-input
            v-model="formData.icon"
            placeholder="图标URL"
            style="margin-top: 10px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm" :loading="submitLoading"
            >确定</el-button
          >
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { imgUrl } from "@/utils/img.js";
import { logger } from "@/utils/logger.js";
import {
  addFurnitureType,
  deleteFurnitureType,
  getFurnitureTypeInfo,
  getFurnitureTypeList,
  updateFurnitureType,
  uploadTypeIcon,
} from "@/api/admin/furnitureType.js";

// --- 状态定义 ---
const loading = ref(false);
const submitLoading = ref(false);
const uploading = ref(false);
const tableData = ref([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const dialogVisible = ref(false);
const isEdit = ref(false);
const formRef = ref(null);

// 搜索表单
const searchForm = ref({
  name: "",
});

// 编辑/新增表单数据
const formData = reactive({
  id: undefined,
  name: "",
  title: "",
  icon: "",
});

// 表单校验规则
const formRules = reactive({
  name: [{ required: true, message: "请输入类型名称", trigger: "blur" }],
  title: [{ required: true, message: "请输入描述", trigger: "blur" }],
  icon: [{ required: true, message: "请上传图标", trigger: "blur" }],
});

const dialogTitle = computed(() => (isEdit.value ? "编辑类型" : "新增类型"));

// --- 方法 ---

// 加载列表
const loadList = async () => {
  loading.value = true;
  try {
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      name: searchForm.value.name || undefined,
    };
    Object.keys(params).forEach((key) => {
      if (params[key] === null || params[key] === "") delete params[key];
    });

    const res = await getFurnitureTypeList(params);
    if (res.success || res.code === 200) {
      tableData.value = res.data.records || res.data || [];
      total.value = res.data.total || res.data.length || 0;
    }
  } catch (error) {
    logger.error("加载失败:", error);
  } finally {
    loading.value = false;
  }
};

// 图标上传处理 - 参考 useProfile.js 的 handleAvatarUpload
const handleIconChange = async (file) => {
  if (!file) return;

  // 文件大小校验（2MB）
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error("图片不能超过2MB");
    return false;
  }

  uploading.value = true;
  try {
    const res = await uploadTypeIcon(file.raw);
    if (res.success || res.code === 200) {
      const iconUrl = res.data;
      formData.icon = iconUrl;
      ElMessage.success("图标上传成功");
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

// 搜索与重置
const handleSearch = () => {
  currentPage.value = 1;
  loadList();
};
const resetSearch = () => {
  searchForm.value = { name: "" };
  handleSearch();
};

// 打开新增弹窗
const handleAdd = () => {
  isEdit.value = false;
  Object.assign(formData, { id: undefined, name: "", title: "", icon: "" });
  dialogVisible.value = true;
};

// 打开编辑弹窗
const handleEdit = async (id) => {
  isEdit.value = true;
  try {
    const res = await getFurnitureTypeInfo(id);
    if (res.success || res.code === 200) {
      Object.assign(formData, res.data);
      dialogVisible.value = true;
    }
  } catch (error) {
    logger.error("获取详情失败:", error);
  }
};

// 删除
const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm("确定删除该类型吗？", "警告", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    const res = await deleteFurnitureType(id);
    if (res.success || res.code === 200) {
      ElMessage.success("删除成功");
      loadList();
    } else {
      ElMessage.error(res.msg || "删除失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      logger.error("删除异常:", error);
    }
  }
};

// 提交表单
const submitForm = async () => {
  try {
    await formRef.value.validate();
    submitLoading.value = true;

    if (isEdit.value) {
      const res = await updateFurnitureType(formData);
      if (res.success || res.code === 200) {
        ElMessage.success("更新成功");
        dialogVisible.value = false;
        loadList();
      } else {
        ElMessage.error(res.msg || "更新失败");
      }
    } else {
      const res = await addFurnitureType(formData);
      if (res.success || res.code === 200) {
        ElMessage.success("新增成功");
        dialogVisible.value = false;
        loadList();
      } else {
        ElMessage.error(res.msg || "新增失败");
      }
    }
  } catch (error) {
    logger.error("操作异常:", error);
  } finally {
    submitLoading.value = false;
  }
};

const handleSizeChange = (val) => {
  currentPage.value = 1;
  loadList();
};

onMounted(() => {
  loadList();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/furniture-type-manage.scss";
</style>
