<template>
  <div class="manage-page">
    <h2 class="page-title">售后处理</h2>

    <!-- 页签 -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange" class="after-sale-tabs">
      <el-tab-pane name="6,7,8">
        <template #label>全部售后 <span class="tab-count">({{ refundCounts.all }})</span></template>
      </el-tab-pane>
      <el-tab-pane name="6,7">
        <template #label>待处理 <span class="tab-count">({{ refundCounts.pending }})</span></template>
      </el-tab-pane>
      <el-tab-pane name="8">
        <template #label>已退款 <span class="tab-count">({{ refundCounts.refunded }})</span></template>
      </el-tab-pane>
    </el-tabs>

    <!-- 表格 -->
    <el-table :data="orderList" v-loading="loading" border>
      <el-table-column prop="id" label="订单号" min-width="140" />
      <el-table-column label="用户" min-width="100">
        <template #default="{ row }">
          {{ row.userName || ("用户" + row.userId) }}
        </template>
      </el-table-column>
      <el-table-column label="商品" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-for="(item, i) in (row.itemList || []).slice(0, 2)" :key="i">
            {{ item.furnitureName }}<template v-if="i < Math.min((row.itemList || []).length, 2) - 1">、</template>
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="totalPrice" label="金额" width="110">
        <template #default="{ row }">
          <span style="color: #d95a5a; font-weight: 600">¥{{ row.totalPrice }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="refundReason" label="退款原因" min-width="160" show-overflow-tooltip />
      <el-table-column prop="refundApplyTime" label="申请时间" width="180" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="refundHandleRemark" label="备注" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.refundHandleRemark">{{ row.refundHandleRemark }}</span>
          <span v-else style="color: #ccc">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <!-- 申请退款中(6)：同意/拒绝 -->
          <template v-if="row.status === 6">
            <el-button type="success" size="small" @click="handleApprove(row)">同意退款</el-button>
            <el-button type="danger" size="small" @click="openRejectDialog(row)">拒绝退款</el-button>
          </template>
          <!-- 退款审核中(7)：审核通过/不通过 -->
          <template v-else-if="row.status === 7">
            <el-button type="success" size="small" @click="handleAuditPass(row)">审核通过</el-button>
            <el-button type="danger" size="small" @click="openAuditFailDialog(row)">审核不通过</el-button>
          </template>
          <!-- 已退款(8)：仅展示最终状态 -->
          <el-tag v-else type="info" size="small">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="loadData"
      />
    </div>

    <!-- 拒绝退款弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="拒绝退款" width="400px">
      <el-form label-position="top">
        <el-form-item label="拒绝原因" :rules="[{ required: true, message: '请填写拒绝原因' }]">
          <el-input
            v-model="rejectForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请填写拒绝原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="submitReject" :loading="submitting">确认拒绝</el-button>
      </template>
    </el-dialog>

    <!-- 审核不通过弹窗 -->
    <el-dialog v-model="auditFailDialogVisible" title="审核不通过" width="400px">
      <el-form label-position="top">
        <el-form-item label="不通过原因" :rules="[{ required: true, message: '请填写不通过原因' }]">
          <el-input
            v-model="auditFailForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请填写审核不通过原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditFailDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="submitAuditFail" :loading="submitting">确认不通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { logger } from "@/utils/logger.js";
import {
  approveRefund,
  auditRefund,
  getOrderList,
  getRefundStatusCounts,
  rejectRefund,
} from "@/api/admin/order.js";

const activeTab = ref("6,7,8");
const loading = ref(false);
const orderList = ref([]);
const refundCounts = ref({ pending: 0, refunded: 0, all: 0 });
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

const submitting = ref(false);
const rejectDialogVisible = ref(false);
const rejectForm = ref({ remark: "" });
const rejectTarget = ref(null);
const auditFailDialogVisible = ref(false);
const auditFailForm = ref({ remark: "" });
const auditFailTarget = ref(null);

const getStatusText = (status) => {
  const map = {
    6: "申请退款中",
    7: "退款审核中",
    8: "已退款",
  };
  return map[status] || "未知";
};

const getStatusType = (status) => {
  const map = {
    6: "warning",
    7: "primary",
    8: "info",
  };
  return map[status] || "info";
};

const loadData = async () => {
  loading.value = true;
  try {
    const params = {
      current: currentPage.value,
      size: pageSize.value,
    };
    if (activeTab.value) {
      params.status = activeTab.value; // "6,7" / "8" / 空
    }
    const res = await getOrderList(params);
    if (res.success || res.code === 200) {
      orderList.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (e) {
    logger.error(e);
  } finally {
    loading.value = false;
  }
};

const handleTabChange = () => {
  currentPage.value = 1;
  loadData();
};

const handleSizeChange = (val) => {
  pageSize.value = val;
  currentPage.value = 1;
  loadData();
};

const loadCounts = async () => {
  try {
    const res = await getRefundStatusCounts();
    if ((res.success || res.code === 200) && res.data) {
      refundCounts.value = {
        pending: res.data.pending || 0,
        refunded: res.data.refunded || 0,
        all: res.data.all || 0,
      };
    }
  } catch (e) {
    logger.error(e);
  }
};

// 同意退款（6 → 7）
const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm(`确定同意订单 #${row.id} 的退款申请吗？`, "同意退款", {
      type: "success",
    });
    const res = await approveRefund(row.id);
    if (res.success || res.code === 200) {
      ElMessage.success("已同意退款，进入审核阶段");
      window.dispatchEvent(new CustomEvent("review-count-update"));
      loadData();
      loadCounts();
    } else {
      ElMessage.error(res.msg || res.message || "操作失败");
    }
  } catch (e) {
    if (e !== "cancel") ElMessage.error("操作失败");
  }
};

// 拒绝退款（6 → 原状态）
const openRejectDialog = (row) => {
  rejectTarget.value = row;
  rejectForm.value = { remark: "" };
  rejectDialogVisible.value = true;
};

const submitReject = async () => {
  if (!rejectForm.value.remark.trim()) {
    ElMessage.warning("请填写拒绝原因");
    return;
  }
  submitting.value = true;
  try {
    const res = await rejectRefund(rejectTarget.value.id, { remark: rejectForm.value.remark.trim() });
    if (res.success || res.code === 200) {
      ElMessage.success("已拒绝退款");
      rejectDialogVisible.value = false;
      window.dispatchEvent(new CustomEvent("review-count-update"));
      loadData();
      loadCounts();
    } else {
      ElMessage.error(res.msg || res.message || "操作失败");
    }
  } catch (e) {
    logger.error(e);
    ElMessage.error("操作失败");
  } finally {
    submitting.value = false;
  }
};

// 审核通过（7 → 8）
const handleAuditPass = async (row) => {
  try {
    await ElMessageBox.confirm(`确定审核通过订单 #${row.id} 的退款吗？通过后将退还货款并释放库存。`, "审核通过", {
      type: "success",
    });
    const res = await auditRefund({ orderId: row.id, passed: true });
    if (res.success || res.code === 200) {
      ElMessage.success("退款审核通过，已退款");
      window.dispatchEvent(new CustomEvent("review-count-update"));
      loadData();
      loadCounts();
    } else {
      ElMessage.error(res.msg || res.message || "操作失败");
    }
  } catch (e) {
    if (e !== "cancel") ElMessage.error("操作失败");
  }
};

// 审核不通过（7 → 原状态）
const openAuditFailDialog = (row) => {
  auditFailTarget.value = row;
  auditFailForm.value = { remark: "" };
  auditFailDialogVisible.value = true;
};

const submitAuditFail = async () => {
  if (!auditFailForm.value.remark.trim()) {
    ElMessage.warning("请填写不通过原因");
    return;
  }
  submitting.value = true;
  try {
    const res = await auditRefund({
      orderId: auditFailTarget.value.id,
      passed: false,
      remark: auditFailForm.value.remark.trim(),
    });
    if (res.success || res.code === 200) {
      ElMessage.success("审核不通过，订单已恢复");
      auditFailDialogVisible.value = false;
      window.dispatchEvent(new CustomEvent("review-count-update"));
      loadData();
      loadCounts();
    } else {
      ElMessage.error(res.msg || res.message || "操作失败");
    }
  } catch (e) {
    logger.error(e);
    ElMessage.error("操作失败");
  } finally {
    submitting.value = false;
  }
};

onMounted(() => {
  loadData();
  loadCounts();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/after-sale-manage.scss";

.tab-count {
  color: #999;
  font-size: 12px;
}
</style>
