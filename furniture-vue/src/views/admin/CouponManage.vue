<template>
  <div class="manage-page">
    <h2 class="page-title">优惠券管理</h2>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchForm.name"
        placeholder="券名称"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
      <el-button type="success" style="margin-left: auto" @click="handleAdd"
        >+ 新增优惠券</el-button
      >
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="name" label="券名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="叠加" width="100">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.stackable === 1 ? 'success' : 'info'"
            :effect="row.stackable === 1 ? 'light' : 'plain'"
          >
            {{ row.stackable === 1 ? "可叠加" : "不可叠加" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.type)">{{ typeText(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="面额 / 规则" width="150">
        <template #default="{ row }">{{ ruleText(row) }}</template>
      </el-table-column>
      <el-table-column label="有效期" min-width="150">
        <template #default="{ row }">{{ validText(row) }}</template>
      </el-table-column>
      <el-table-column label="领取人群" width="110">
        <template #default="{ row }">{{ targetText(row) }}</template>
      </el-table-column>
      <el-table-column label="每人限领" width="90">
        <template #default="{ row }">{{ row.perUserLimit }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            @change="(v) => handleToggle(row, v)"
          />
        </template>
      </el-table-column>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" top="4vh" class="coupon-dialog">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="券名称" prop="name">
              <el-input v-model="formData.name" placeholder="如：新人大额满减券" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="券类型" prop="type">
              <el-radio-group v-model="formData.type">
                <el-radio :value="1">满减</el-radio>
                <el-radio :value="2">折扣</el-radio>
                <el-radio :value="3">无门槛</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="门槛金额">
              <el-input-number v-model="formData.minThreshold" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="formData.type !== 2" label="面额">
              <el-input-number v-model="formData.amount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item v-else label="折扣率">
              <el-input-number v-model="formData.discount" :min="0.1" :max="0.95" :step="0.05" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="formData.type === 2" label="折扣上限">
          <el-input-number v-model="formData.capAmount" :min="0" :precision="2" style="width: 200px" />
          <span style="margin-left: 10px; color: var(--color-text-tertiary); font-size: 13px">最高可优惠金额（可空）</span>
        </el-form-item>

        <el-divider content-position="left">适用范围与发放</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="适用范围">
              <el-radio-group v-model="formData.scope">
                <el-radio :value="0">全场</el-radio>
                <el-radio :value="1">按分类</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="formData.scope === 1" label="选择分类">
              <el-select v-model="formData.typeId" placeholder="请选择分类" style="width: 100%">
                <el-option v-for="t in typeOptions" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发放总量">
              <el-input-number v-model="formData.totalCount" :min="0" style="width: 100%" />
              <span style="color: var(--color-text-tertiary); font-size: 12px">0 或空 = 不限</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="每人限领" prop="perUserLimit">
              <el-input-number v-model="formData.perUserLimit" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">领取与有效期</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="领取开始">
              <el-date-picker v-model="formData.claimStart" type="datetime" placeholder="不限" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="领取结束">
              <el-date-picker v-model="formData.claimEnd" type="datetime" placeholder="不限" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="有效期" prop="validType">
          <el-radio-group v-model="formData.validType" @change="onValidTypeChange">
            <el-radio :value="1">固定期限</el-radio>
            <el-radio :value="2">领取后 N 天</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-row v-if="formData.validType === 1" :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker v-model="formData.validStart" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期">
              <el-date-picker v-model="formData.validEnd" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-else label="有效天数">
          <el-input-number v-model="formData.validDays" :min="1" style="width: 200px" />
        </el-form-item>

        <el-divider content-position="left">领取人群与状态</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="领取人群">
              <el-radio-group v-model="formData.targetType">
                <el-radio :value="0">不限</el-radio>
                <el-radio :value="1">新用户</el-radio>
                <el-radio :value="2">老用户</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="formData.targetType !== 0" label="判定天数">
              <el-input-number v-model="formData.targetDays" :min="1" style="width: 200px" />
              <span style="color: var(--color-text-tertiary); font-size: 12px">注册≤N=新用户</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="启用状态">
          <el-switch v-model="formData.statusOn" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="叠加使用">
          <el-switch
            v-model="formData.stackable"
            :active-value="1"
            :inactive-value="0"
            active-text="可叠加"
            inactive-text="不可叠加"
          />
          <div class="form-tip">可叠加券可多张同时使用；不可叠加券一单只能用一张</div>
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
import { logger } from "@/utils/logger.js";
import {
  addCoupon,
  deleteCoupon,
  getCouponInfo,
  getCouponList,
  toggleCoupon,
  updateCoupon,
} from "@/api/admin/coupon";
import { getFurnitureTypeList } from "@/api/admin/furnitureType";

const loading = ref(false);
const submitLoading = ref(false);
const tableData = ref([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const dialogVisible = ref(false);
const isEdit = ref(false);
const formRef = ref(null);
const typeOptions = ref([]);

const searchForm = ref({ name: "" });

const emptyForm = () => ({
  id: undefined,
  name: "",
  type: 1,
  minThreshold: 0,
  amount: 0,
  discount: 0.8,
  capAmount: undefined,
  scope: 0,
  typeId: undefined,
  totalCount: 0,
  perUserLimit: 1,
  claimStart: "",
  claimEnd: "",
  validType: 1,
  validStart: "",
  validEnd: "",
  validDays: 7,
  targetType: 0,
  targetDays: 30,
  stackable: 0,
  statusOn: true,
});

const formData = reactive(emptyForm());

const formRules = reactive({
  name: [{ required: true, message: "请输入券名称", trigger: "blur" }],
  type: [{ required: true, message: "请选择券类型", trigger: "change" }],
  perUserLimit: [{ required: true, message: "请填写每人限领数", trigger: "blur" }],
  validType: [{ required: true, message: "请选择有效期模式", trigger: "change" }],
});

const dialogTitle = computed(() => (isEdit.value ? "编辑优惠券" : "新增优惠券"));

const typeText = (t) => (t === 2 ? "折扣" : t === 3 ? "无门槛" : "满减");
const typeTag = (t) => (t === 2 ? "warning" : t === 3 ? "info" : "success");

const ruleText = (row) => {
  let t;
  if (row.type === 2 && row.discount != null) {
    t = String(Number(row.discount) * 10).replace(/\.0$/, "") + "折";
  } else if (row.amount != null) {
    t = "¥" + Number(row.amount);
  } else {
    t = "-";
  }
  if (row.type !== 2 && Number(row.minThreshold) > 0) {
    t += ` / 满${row.minThreshold}`;
  }
  return t;
};

const validText = (row) => {
  if (row.validType === 2) return `领取后${row.validDays}天`;
  return row.validEnd ? String(row.validEnd).slice(0, 10) + " 止" : "不限";
};

const targetText = (row) => {
  if (row.targetType === 1) return "新用户";
  if (row.targetType === 2) return "老用户";
  return "不限";
};

const loadList = async () => {
  loading.value = true;
  try {
    const params = { current: currentPage.value, size: pageSize.value, name: searchForm.value.name || undefined };
    Object.keys(params).forEach((k) => (params[k] === "" ? delete params[k] : null));
    const res = await getCouponList(params);
    if (res.success || res.code === 200) {
      tableData.value = res.data.records || res.data || [];
      total.value = res.data.total || res.data.length || 0;
    }
  } catch (e) {
    logger.error("加载优惠券失败:", e);
  } finally {
    loading.value = false;
  }
};

const loadTypes = async () => {
  try {
    const res = await getFurnitureTypeList({ current: 1, size: 100 });
    if (res.success || res.code === 200) {
      typeOptions.value = res.data.records || res.data || [];
    }
  } catch (e) {
    logger.error("加载分类失败:", e);
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  loadList();
};
const resetSearch = () => {
  searchForm.value = { name: "" };
  handleSearch();
};

const handleAdd = () => {
  isEdit.value = false;
  Object.assign(formData, emptyForm());
  dialogVisible.value = true;
};

const handleEdit = async (id) => {
  isEdit.value = true;
  try {
    const res = await getCouponInfo(id);
    if (res.success || res.code === 200) {
      Object.assign(formData, res.data);
      formData.statusOn = (res.data.status ?? 1) === 1;
      dialogVisible.value = true;
    }
  } catch (e) {
    logger.error("获取详情失败:", e);
  }
};

const handleToggle = async (row, on) => {
  try {
    const res = await toggleCoupon(row.id);
    if (res.success || res.code === 200) {
      ElMessage.success(res.msg || "操作成功");
      row.status = on ? 1 : 0;
    } else {
      ElMessage.error(res.msg || "操作失败");
    }
  } catch (e) {
    logger.error("切换状态失败:", e);
  }
};

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm("确定删除该优惠券吗？", "警告", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    const res = await deleteCoupon(id);
    if (res.success || res.code === 200) {
      ElMessage.success("删除成功");
      loadList();
    } else {
      ElMessage.error(res.msg || "删除失败");
    }
  } catch (e) {
    if (e !== "cancel") logger.error("删除异常:", e);
  }
};

const onValidTypeChange = () => {
  if (formData.validType === 1) formData.validDays = undefined;
};

const buildPayload = () => {
  const d = { ...formData };
  d.status = d.statusOn ? 1 : 0;
  delete d.statusOn;
  // 空字符串→null，避免后端补齐为字符串
  ["claimStart", "claimEnd", "validStart", "validEnd"].forEach((k) => {
    if (!d[k]) d[k] = null;
  });
  if (d.totalCount === 0) d.totalCount = null;
  return d;
};

const submitForm = async () => {
  try {
    await formRef.value.validate();
    submitLoading.value = true;
    const payload = buildPayload();
    if (isEdit.value) {
      const res = await updateCoupon(payload);
      if (res.success || res.code === 200) {
        ElMessage.success("更新成功");
        dialogVisible.value = false;
        loadList();
      } else {
        ElMessage.error(res.msg || "更新失败");
      }
    } else {
      const res = await addCoupon(payload);
      if (res.success || res.code === 200) {
        ElMessage.success("新增成功");
        dialogVisible.value = false;
        loadList();
      } else {
        ElMessage.error(res.msg || "新增失败");
      }
    }
  } catch (e) {
    if (e !== "cancel") logger.error("操作异常:", e);
  } finally {
    submitLoading.value = false;
  }
};

const handleSizeChange = () => {
  currentPage.value = 1;
  loadList();
};

onMounted(() => {
  loadList();
  loadTypes();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/coupon-manage.scss";

.form-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #999);
  line-height: 1.4;
}
</style>