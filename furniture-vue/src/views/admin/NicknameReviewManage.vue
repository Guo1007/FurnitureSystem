<template>
  <div class="profile-review-manage">
    <div class="page-header">
      <h2>昵称审核</h2>
      <p class="page-desc">审核用户修改的昵称</p>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane name="">
        <template #label>全部 <span class="tab-count">({{ nicknameCounts.all }})</span></template>
      </el-tab-pane>
      <el-tab-pane name="1">
        <template #label>待审核 <span class="tab-count">({{ nicknameCounts.pending }})</span></template>
      </el-tab-pane>
      <el-tab-pane name="0">
        <template #label>已通过 <span class="tab-count">({{ nicknameCounts.approved }})</span></template>
      </el-tab-pane>
      <el-tab-pane name="2">
        <template #label>已拒绝 <span class="tab-count">({{ nicknameCounts.rejected }})</span></template>
      </el-tab-pane>
      <el-tab-pane name="3">
        <template #label>待复审 <span class="tab-count">({{ nicknameCounts.reReview }})</span></template>
      </el-tab-pane>
    </el-tabs>

    <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
      <el-table-column label="用户昵称" min-width="1">
        <template #default="{ row }">
          <span>{{ row.userName || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="修改后昵称" min-width="1">
        <template #default="{ row }">
          <span class="pending-value">{{ row.pendingNickname }}</span>
        </template>
      </el-table-column>
      <el-table-column label="AI拒绝原因" min-width="1" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.aiRejectReason" class="text-danger">{{ row.aiRejectReason }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="拒绝原因" min-width="1" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.manualRejectReason" class="text-danger">{{ row.manualRejectReason }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.reviewStatus === 0" type="success" size="small">已通过</el-tag>
          <el-tag v-else-if="row.reviewStatus === 1" type="warning" size="small">待审核</el-tag>
          <el-tag v-else-if="row.reviewStatus === 2" type="danger" size="small">已拒绝</el-tag>
          <el-tag v-else-if="row.reviewStatus === 3" type="warning" size="small">待复审</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="1" align="center">
        <template #default="{ row }">
          <template v-if="row.reviewStatus === 1 || row.reviewStatus === 3">
            <el-button type="success" size="small" @click="approve(row)">通过</el-button>
            <el-button type="danger" size="small" @click="handleReject(row)">拒绝</el-button>
          </template>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </div>

    <el-dialog v-model="rejectDialog.visible" title="拒绝原因" width="480px" @closed="rejectDialog.reason = ''">
      <el-form label-width="80px">
        <el-form-item label="选择模板">
          <el-select
            v-model="rejectDialog.reason"
            placeholder="选择常用拒绝原因"
            clearable
            style="width: 100%"
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
            v-model="rejectDialog.reason"
            type="textarea"
            :rows="2"
            placeholder="请输入或选择拒绝原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialog.visible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getNicknameReviewList,
  getProfileReviewStatusCounts,
  approveNickname,
  rejectNickname,
} from "@/api/admin/profileReview.js";
import { getRejectReasons } from "@/api/admin/rejectReason.js";
import { logger } from "@/utils/logger.js";

const loading = ref(false);
const tableData = ref([]);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const activeTab = ref("");
const rejectReasons = ref([]);
const nicknameCounts = ref({ all: 0, pending: 0, approved: 0, rejected: 0, reReview: 0 });

const rejectDialog = reactive({
  visible: false,
  reason: "",
  currentRow: null,
});

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getNicknameReviewList(page.value, pageSize.value, activeTab.value || undefined);
    if (res.success || res.code === 200) {
      tableData.value = res.data?.records || [];
      total.value = res.data?.total || 0;
    }
  } catch (e) {
    logger.error(e);
  } finally {
    loading.value = false;
  }
};

const loadRejectReasons = async () => {
  try {
    const res = await getRejectReasons();
    if (res.success || res.code === 200) {
      rejectReasons.value = res.data || [];
    }
  } catch (e) { /* ignore */ }
};

const loadCounts = async () => {
  try {
    const res = await getProfileReviewStatusCounts();
    if ((res.success || res.code === 200) && res.data?.nickname) {
      const n = res.data.nickname;
      nicknameCounts.value = {
        all: n.all || 0,
        pending: n.pending || 0,
        approved: n.approved || 0,
        rejected: n.rejected || 0,
        reReview: n.reReview || 0,
      };
    }
  } catch (e) { logger.error(e); }
};

const onTabChange = () => {
  page.value = 1;
  fetchData();
};

const approve = (row) => {
  ElMessageBox.confirm("确认通过「" + row.pendingNickname + "」？", "确认审核", { type: "info" }).then(async () => {
    try {
      const res = await approveNickname(row.userId);
      if (res.success || res.code === 200) {
        ElMessage.success("审核通过");
        window.dispatchEvent(new CustomEvent("review-count-update"));
        fetchData();
        loadCounts();
      }
    } catch (e) {
      logger.error(e);
    }
  }).catch(() => {});
};

const handleReject = (row) => {
  rejectDialog.currentRow = row;
  rejectDialog.reason = "";
  rejectDialog.visible = true;
};

const confirmReject = async () => {
  const row = rejectDialog.currentRow;
  if (!row) return;
  try {
    const res = await rejectNickname(row.userId, rejectDialog.reason);
    if (res.success || res.code === 200) {
      ElMessage.success("已拒绝");
      window.dispatchEvent(new CustomEvent("review-count-update"));
      rejectDialog.visible = false;
      fetchData();
      loadCounts();
    }
  } catch (e) {
    logger.error(e);
  }
};

onMounted(() => {
  fetchData();
  loadRejectReasons();
  loadCounts();
});
</script>

<style scoped>
.profile-review-manage {
  padding: 6px 0;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 6px 0;
  font-size: 20px;
  color: #333;
  font-weight: 600;
}

.page-desc {
  margin: 0;
  color: #999;
  font-size: 13px;
}

.pending-value {
  color: #333;
  font-weight: 500;
}

.text-muted {
  color: #bbb;
}

.text-danger {
  color: #e35d5d;
}

.tab-count {
  color: #999;
  font-size: 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 16px;
  padding-top: 12px;
}
</style>