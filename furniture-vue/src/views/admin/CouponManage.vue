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
      <el-button type="warning" style="margin-left: auto" @click="handleOpenGrant"
        >定向发放</el-button
      >
      <el-button type="success" @click="handleAdd">+ 新增优惠券</el-button>
    </div>

    <!-- 叠加规则设置 -->
    <div class="rule-panel">
      <div class="rule-panel__head">
        <span class="rule-panel__title">叠加规则设置</span>
        <span class="rule-panel__hint"
          >控制多张优惠券同时使用时的上限，对所有优惠券生效</span
        >
      </div>

      <div v-if="ruleLoading" class="rule-panel__loading">加载中…</div>

      <div v-else class="rule-panel__body">
        <div v-for="rule in ruleList" :key="rule.ruleKey" class="rule-item">
          <div class="rule-item__main">
            <span class="rule-item__name">{{ rule.ruleName }}</span>
            <el-input-number
              v-model="rule.ruleValueNum"
              :min="rule.minValue"
              :max="rule.maxValue"
              size="small"
              controls-position="right"
              style="width: 130px"
            />
            <span class="rule-item__unit">{{ rule.unit }}</span>
          </div>
          <div class="rule-item__side">
            <el-switch
              v-model="rule.enabled"
              size="small"
              active-text="启用"
              inactive-text="停用"
            />
            <el-button
              type="primary"
              size="small"
              :loading="ruleSaving === rule.ruleKey"
              @click="saveRule(rule)"
              >保存</el-button
            >
          </div>
          <div class="rule-item__remark">{{ rule.remark }}</div>
        </div>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="name" label="券名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="发放方式" width="110">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.issueType === 2 ? 'warning' : 'info'"
            effect="plain"
          >
            {{ row.issueType === 2 ? "定向发放" : "公开领取" }}
          </el-tag>
        </template>
      </el-table-column>
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
        <el-form-item label="发放方式">
          <el-radio-group v-model="formData.issueType">
            <el-radio :value="1">公开领取</el-radio>
            <el-radio :value="2">定向发放</el-radio>
          </el-radio-group>
          <div class="form-tip">
            公开领取：上架到领券中心由用户自行领取，受发放总量与每人限领约束。<br />
            定向发放：不进领券中心，只能由管理员在「定向发放」里发给指定用户（补偿／关怀用），
            不受发放总量与每人限领约束。
          </div>
        </el-form-item>
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
        <el-row :gutter="16" v-if="formData.issueType === 1">
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
        <el-row :gutter="16" v-if="formData.issueType === 1">
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
        <el-row :gutter="16" v-if="formData.issueType === 1">
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

    <!-- 定向发放弹窗：选券 + 选用户 + 通知渠道 -->
    <el-dialog
      v-model="grantVisible"
      title="定向发放优惠券"
      width="620px"
      top="8vh"
      class="grant-dialog"
    >
      <el-form
        ref="grantFormRef"
        :model="grantForm"
        :rules="grantRules"
        label-width="100px"
      >
        <el-form-item label="优惠券" prop="couponId">
          <el-select
            v-model="grantForm.couponId"
            placeholder="请选择要发放的券"
            style="width: 100%"
            :loading="grantCouponLoading"
          >
            <el-option
              v-for="c in grantCouponOptions"
              :key="c.id"
              :label="`${c.name}（${ruleText(c)}）`"
              :value="c.id"
            />
          </el-select>
          <div class="form-tip">
            这里只列出「定向发放」类型的券。公开领取的券受总量与限领约束，
            不能定向发放 —— 需要的话请先复制一张并改为定向发放。
          </div>
        </el-form-item>

        <el-form-item label="接收用户" prop="userIds">
          <el-select
            v-model="grantForm.userIds"
            multiple
            filterable
            placeholder="搜索用户名或邮箱，可多选"
            style="width: 100%"
            :filter-method="searchUsers"
            @focus="loadUsers"
          >
            <el-option
              v-for="u in userList"
              :key="u.id"
              :label="`${u.userName} (${u.email || '无邮箱'})`"
              :value="u.id"
            />
          </el-select>
          <div class="form-tip">已选 {{ grantForm.userIds.length }} 人</div>
        </el-form-item>

        <el-form-item label="每人发放" prop="quantity">
          <el-input-number v-model="grantForm.quantity" :min="1" :max="10" />
          <span style="margin-left: 8px; color: var(--color-text-tertiary)">张</span>
        </el-form-item>

        <el-form-item label="发放场景" prop="scene">
          <el-radio-group v-model="grantForm.scene">
            <el-radio value="compensation">补偿</el-radio>
            <el-radio value="care">关怀</el-radio>
            <el-radio value="general">通用</el-radio>
          </el-radio-group>
          <div class="form-tip">
            决定邮件与站内通知的语气。补偿场景的文案刻意克制 —— 收到的人多半刚遇到问题，
            不要在原因里写促销措辞。
          </div>
        </el-form-item>

        <el-form-item label="通知用户">
          <el-checkbox v-model="grantForm.sendNotification"
            >发送站内通知（推荐）</el-checkbox
          >
          <el-checkbox v-model="grantForm.sendEmail">同时发送邮件</el-checkbox>
          <div class="form-tip">
            站内通知送达可靠；邮件可能被误判为垃圾邮件，建议作为补充。
            无邮箱的用户会自动跳过邮件。
          </div>
        </el-form-item>

        <el-form-item label="发放原因">
          <el-input
            v-model="grantForm.remark"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="可选。会出现在通知与邮件里，便于日后追溯为什么发这张券"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="grantVisible = false">取消</el-button>
          <el-button type="primary" :loading="grantLoading" @click="submitGrant"
            >确认发放</el-button
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
  getCouponRules,
  grantCoupons,
  saveCouponRule,
  toggleCoupon,
  updateCoupon,
} from "@/api/admin/coupon";
import { getFurnitureTypeList } from "@/api/admin/furnitureType";
import { getSimpleUserList } from "@/api/admin/user";

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
  // 发放方式：1-公开领取（默认，与改动前一致），2-定向发放
  issueType: 1,
});

const formData = reactive(emptyForm());

const formRules = reactive({
  name: [{ required: true, message: "请输入券名称", trigger: "blur" }],
  type: [{ required: true, message: "请选择券类型", trigger: "change" }],
  perUserLimit: [{ required: true, message: "请填写每人限领数", trigger: "blur" }],
  validType: [{ required: true, message: "请选择有效期模式", trigger: "change" }],
});

const dialogTitle = computed(() => (isEdit.value ? "编辑优惠券" : "新增优惠券"));

// ---------- 叠加规则设置 ----------
const ruleList = ref([]);
const ruleLoading = ref(false);
const ruleSaving = ref("");

const loadRules = async () => {
  ruleLoading.value = true;
  try {
    const res = await getCouponRules();
    const rows = Array.isArray(res?.data) ? res.data : [];
    ruleList.value = rows.map((r) => ({
      ...r,
      // 后端返回字符串，el-input-number 需要数值
      ruleValueNum: Number.parseInt(r.ruleValue, 10) || r.minValue || 1,
    }));
  } catch (e) {
    logger.error("加载优惠券叠加规则失败:", e);
    ElMessage.warning("叠加规则加载失败，下单将使用默认规则");
  } finally {
    ruleLoading.value = false;
  }
};

const saveRule = async (rule) => {
  const n = Number(rule.ruleValueNum);
  if (!Number.isInteger(n) || n < rule.minValue || n > rule.maxValue) {
    ElMessage.warning(`请输入 ${rule.minValue} ~ ${rule.maxValue} 之间的整数`);
    return;
  }
  ruleSaving.value = rule.ruleKey;
  try {
    const res = await saveCouponRule({
      ruleKey: rule.ruleKey,
      ruleValue: String(n),
      enabled: rule.enabled,
    });
    if (res?.success || res?.code === 200) {
      ElMessage.success("已保存，立即对后续下单生效");
      rule.ruleValue = String(n);
    } else {
      ElMessage.error(res?.msg || res?.message || "保存失败");
    }
  } catch (e) {
    logger.error("保存优惠券叠加规则失败:", e);
    ElMessage.error("保存失败");
  } finally {
    ruleSaving.value = "";
  }
};

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
      // 分页总数在后端响应的顶层（Result.total），不在 data 里。
      // 原先写的是 res.data.total || res.data.length，前者永远取不到、后者等于当前页条数，
      // 结果 total 恒等于每页条数，翻页器永远只有一页。
      total.value = Number(res.total) || 0;
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

// ==================== 定向发放 ====================
const grantVisible = ref(false);
const grantLoading = ref(false);
const grantCouponLoading = ref(false);
const grantFormRef = ref(null);
/** 可发放的券：只含「定向发放」类型（后端按 issueType=2 过滤） */
const grantCouponOptions = ref([]);
const userList = ref([]);

const emptyGrantForm = () => ({
  couponId: undefined,
  userIds: [],
  quantity: 1,
  scene: "general",
  sendNotification: true,
  sendEmail: false,
  remark: "",
});
const grantForm = reactive(emptyGrantForm());

const grantRules = reactive({
  couponId: [{ required: true, message: "请选择要发放的券", trigger: "change" }],
  userIds: [
    {
      required: true,
      type: "array",
      min: 1,
      message: "请至少选择一个用户",
      trigger: "change",
    },
  ],
  quantity: [{ required: true, message: "请填写发放张数", trigger: "blur" }],
});

const handleOpenGrant = async () => {
  Object.assign(grantForm, emptyGrantForm());
  grantVisible.value = true;
  grantCouponOptions.value = [];
  grantCouponLoading.value = true;
  try {
    // issueType=2：让后端筛，别拉全量在前端过滤（券模板多了会漏）
    const res = await getCouponList({ current: 1, size: 200, issueType: 2 });
    if (res.success || res.code === 200) {
      grantCouponOptions.value = res.data?.records || res.data || [];
    } else {
      ElMessage.error(res.msg || "加载可发放的券失败");
    }
  } catch (e) {
    logger.error("加载可发放的券失败:", e);
  } finally {
    grantCouponLoading.value = false;
  }
  loadUsers();
};

const loadUsers = async (keyword) => {
  try {
    const res = await getSimpleUserList(keyword);
    if (res.success || res.code === 200) {
      userList.value = res.data || [];
    }
  } catch (e) {
    logger.error("加载用户列表失败:", e);
  }
};
const searchUsers = (kw) => loadUsers(kw);

const submitGrant = async () => {
  try {
    await grantFormRef.value.validate();
  } catch {
    return; // 校验未通过，el-form 自己会标红
  }
  grantLoading.value = true;
  try {
    const res = await grantCoupons({ ...grantForm });
    if (res.success || res.code === 200) {
      ElMessage.success(res.msg || "发放成功");
      grantVisible.value = false;
    } else {
      ElMessage.error(res.msg || "发放失败");
    }
  } catch (e) {
    logger.error("定向发放失败:", e);
  } finally {
    grantLoading.value = false;
  }
};

onMounted(() => {
  loadList();
  loadTypes();
  loadRules();
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

/* 叠加规则设置面板 */
.rule-panel {
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
  background: var(--el-fill-color-lighter, #fafafa);

  &__head {
    display: flex;
    align-items: baseline;
    gap: 10px;
    margin-bottom: 12px;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
  }

  &__hint {
    font-size: 12px;
    color: var(--el-text-color-secondary, #909399);
  }

  &__loading {
    font-size: 13px;
    color: var(--el-text-color-secondary, #909399);
  }

  &__body {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
  }
}

.rule-item {
  flex: 1 1 320px;
  min-width: 300px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 6px;
  background: #fff;

  &__main {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__name {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
  }

  &__unit {
    font-size: 13px;
    color: var(--el-text-color-secondary, #909399);
  }

  &__side {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-top: 10px;
  }

  &__remark {
    margin-top: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary, #909399);
    line-height: 1.5;
  }
}
</style>