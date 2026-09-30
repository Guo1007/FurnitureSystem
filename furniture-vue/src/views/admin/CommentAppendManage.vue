<template>
  <div class="manage-page">
    <h2 class="page-title">追评审核</h2>

    <div class="toolbar">
      <el-button
        type="danger"
        :disabled="selectedAppends.length === 0"
        @click="handleBatchDelete"
      >
        批量删除
        <span v-if="selectedAppends.length > 0"
          >({{ selectedAppends.length }})</span
        >
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane name="all">
        <template #label>全部 <span class="tab-count">({{ tabCounts.all }})</span></template>
      </el-tab-pane>
      <el-tab-pane name="pending">
        <template #label>待审批 <span class="tab-count">({{ tabCounts.pending }})</span></template>
      </el-tab-pane>
      <el-tab-pane name="approved">
        <template #label>已通过 <span class="tab-count">({{ tabCounts.approved }})</span></template>
      </el-tab-pane>
      <el-tab-pane name="rejected">
        <template #label>已拒绝 <span class="tab-count">({{ tabCounts.rejected }})</span></template>
      </el-tab-pane>
    </el-tabs>

    <el-table
      :data="tableData"
      v-loading="loading"
      border
      @selection-change="(val) => (selectedAppends = val)"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column prop="userName" label="用户" width="120" />
      <el-table-column
        prop="goodsName"
        label="商品"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column prop="appendNum" label="第几次" width="80" />
      <el-table-column
        prop="appendContent"
        label="追评内容"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column label="图片" width="120">
        <template #default="{ row }">
          <div
            v-if="parseJson(row.appendImg).length > 0"
            class="media-preview"
          >
            <img
              loading="lazy"
              v-for="(img, idx) in parseJson(row.appendImg).slice(0, 2)"
              :key="idx"
              :src="img"
              class="thumb-img"
              @click="previewImage(img)"
            />
            <span
              v-if="parseJson(row.appendImg).length > 2"
              class="more-count"
              >+{{ parseJson(row.appendImg).length - 2 }}</span
            >
          </div>
          <span v-else class="no-media">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning" size="small"
            >待审核</el-tag
          >
          <el-tag v-else-if="row.status === 1" type="success" size="small"
            >已通过</el-tag
          >
          <el-tag v-else-if="row.status === 3" type="danger" size="small"
            >待人工复审</el-tag
          >
          <el-tag v-else type="info" size="small">已拒绝</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="activeTab === 'pending'"
        prop="aiRejectReason"
        label="AI拒绝原因"
        min-width="150"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span v-if="row.status === 3">{{ row.aiRejectReason || "-" }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column
        v-if="activeTab === 'rejected'"
        prop="manualRejectReason"
        label="拒绝原因"
        min-width="150"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span>{{ row.manualRejectReason || "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="appendTime" label="时间" width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0 || row.status === 3">
            <el-button-group>
              <el-button
                type="success"
                size="small"
                @click="handleApprove(row)"
                >通过</el-button
              >
              <el-button
                type="warning"
                size="small"
                plain
                @click="openReject(row)"
                >拒绝</el-button
              >
            </el-button-group>
            <el-button
              type="danger"
              size="small"
              text
              @click="handleDelete(row.id)"
              style="margin-left: 4px"
              >删除</el-button
            >
          </template>
          <template v-else>
            <el-button
              type="danger"
              size="small"
              text
              @click="handleDelete(row.id)"
              >删除</el-button
            >
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
      />
    </div>

    <!-- 图片预览 -->
    <el-dialog v-model="imagePreviewVisible" title="图片预览" width="600px">
      <img loading="lazy" :src="previewImageUrl" style="width: 100%; border-radius: 6px" />
    </el-dialog>

    <!-- 拒绝原因弹窗 -->
    <el-dialog
      v-model="rejectVisible"
      title="拒绝原因"
      width="480px"
      @closed="resetReject"
    >
      <div class="reject-body">
        <el-form label-width="80px">
          <el-form-item label="选择模板">
            <el-select
              v-model="selectedTemplate"
              placeholder="选择常用拒绝原因"
              clearable
              style="width: 100%"
              @change="onTemplateChange"
            >
              <el-option
                v-for="r in rejectReasons"
                :key="r.id"
                :label="r.reason"
                :value="r.reason"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="拒绝原因">
            <el-input
              v-model="rejectForm.reason"
              type="textarea"
              :rows="3"
              placeholder="请输入或选择拒绝原因"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="handleReject" :loading="rejecting"
          >确认拒绝</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  approveAppend,
  batchDeleteAppends,
  deleteAppend,
  getPendingAppends,
  getStatusCounts,
  rejectAppend,
} from "@/api/admin/comment.js";
import { getRejectReasons } from "@/api/admin/rejectReason.js";
import { logger } from "@/utils/logger.js";

const tableData = ref([]);
const total = ref(0);
const tabCounts = ref({ all: 0, pending: 0, approved: 0, rejected: 0 });
const loading = ref(false);
const activeTab = ref("all");
const page = ref(1);
const pageSize = 10;
const selectedAppends = ref([]);

const imagePreviewVisible = ref(false);
const previewImageUrl = ref("");

// 拒绝原因弹窗
const rejectVisible = ref(false);
const rejecting = ref(false);
const rejectReasons = ref([]);
const selectedTemplate = ref("");
const rejectForm = ref({ reason: "" });
const currentRejectRow = ref(null);

const statusMap = {
  all: undefined,
  pending: "0,3",
  approved: "1",
  rejected: "2",
};

const onTabChange = () => {
  page.value = 1;
  selectedAppends.value = [];
  loadData();
};

const previewImage = (url) => {
  previewImageUrl.value = url;
  imagePreviewVisible.value = true;
};

const parseJson = (str) => {
  if (!str) return [];
  try {
    const arr = JSON.parse(str);
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const params = {
      current: page.value,
      size: pageSize,
    };
    const statuses = statusMap[activeTab.value];
    if (statuses) {
      params.statuses = statuses;
    }
    const res = await getPendingAppends(params);
    if (res.success || res.code === 200) {
      tableData.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (e) {
    logger.error(e);
  } finally {
    loading.value = false;
  }
};

const loadCounts = async () => {
  try {
    const res = await getStatusCounts();
    if ((res.success || res.code === 200) && res.data) {
      tabCounts.value = {
        all: res.data.append.all,
        pending: res.data.append.pending,
        approved: res.data.append.approved,
        rejected: res.data.append.rejected,
      };
    }
  } catch (e) {
    /* ignore */
  }
};

const loadRejectReasons = async () => {
  try {
    const res = await getRejectReasons();
    if (res.success || res.code === 200) {
      rejectReasons.value = res.data || [];
    }
  } catch (e) {
    /* ignore */
  }
};

const openReject = (row) => {
  currentRejectRow.value = row;
  rejectForm.value.reason = "";
  selectedTemplate.value = "";
  rejectVisible.value = true;
};

const onTemplateChange = (val) => {
  rejectForm.value.reason = val || "";
};

const resetReject = () => {
  currentRejectRow.value = null;
  rejectForm.value.reason = "";
  selectedTemplate.value = "";
};

const handleReject = async () => {
  if (!rejectForm.value.reason.trim()) {
    ElMessage.warning("请输入拒绝原因");
    return;
  }
  rejecting.value = true;
  try {
    const res = await rejectAppend(currentRejectRow.value.id, {
      rejectReason: rejectForm.value.reason,
    });
    if (res.success || res.code === 200) {
      ElMessage.success("已拒绝");
      window.dispatchEvent(new CustomEvent("review-count-update"));
      rejectVisible.value = false;
      loadData();
      loadCounts();
    }
  } catch (e) {
    if (e !== "cancel") ElMessage.error("操作失败");
  } finally {
    rejecting.value = false;
  }
};

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm("确定通过该追评吗？", "审核确认", {
      type: "success",
    });
    const res = await approveAppend(row.id);
    if (res.success || res.code === 200) {
      ElMessage.success("审核通过");
      window.dispatchEvent(new CustomEvent("review-count-update"));
      loadData();
      loadCounts();
    }
  } catch (e) {
    if (e !== "cancel") ElMessage.error("操作失败");
  }
};

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm("确定删除该追评吗？", "确认删除", {
      type: "warning",
    });
    const res = await deleteAppend(id);
    if (res.success || res.code === 200) {
      ElMessage.success("删除成功");
      loadData();
      loadCounts();
    }
  } catch (e) {
    if (e !== "cancel") ElMessage.error("操作失败");
  }
};

const handleBatchDelete = async () => {
  const ids = selectedAppends.value.map((r) => r.id);
  if (ids.length === 0) return;
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${ids.length} 条追评吗？`,
      "批量删除",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    const res = await batchDeleteAppends(ids);
    if (res.success || res.code === 200) {
      ElMessage.success(`已删除 ${ids.length} 条`);
      selectedAppends.value = [];
      loadData();
      loadCounts();
    }
  } catch (e) {
    if (e !== "cancel") ElMessage.error("操作失败");
  }
};

onMounted(() => {
  loadData();
  loadCounts();
  loadRejectReasons();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/comment-manage.scss";

.reject-body {
  padding: 8px 0;
}

.tab-count {
  color: var(--color-text-tertiary);
  font-size: 12px;
}
</style>