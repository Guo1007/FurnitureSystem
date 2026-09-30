<template>
  <div class="operation-log-manage manage-page">
    <h2 class="page-title">操作日志</h2>
    <p class="page-desc">查看系统所有操作记录，支持按用户、操作、结果和时间范围筛选</p>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input
        v-model="filters.userName"
        placeholder="操作用户"
        clearable
        style="width: 160px"
        @clear="search"
        @keyup.enter="search"
      />
      <el-input
        v-model="filters.operation"
        placeholder="操作描述"
        clearable
        style="width: 160px"
        @clear="search"
        @keyup.enter="search"
      />
      <el-select
        v-model="filters.resultStatus"
        placeholder="结果"
        clearable
        style="width: 120px"
        @change="search"
        @clear="search"
      >
        <el-option label="成功" value="成功" />
        <el-option label="失败" value="失败" />
      </el-select>
      <el-date-picker
        v-model="filters.timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        format="YYYY-MM-DD HH:mm"
        value-format="YYYY-MM-DDTHH:mm:ss"
        @change="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
      <!-- <el-table-column prop="id" label="ID" width="80" /> -->
      <el-table-column prop="userName" label="操作用户" width="160">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.userName || row.userName === '匿名' }">
            {{ row.userName || '匿名' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="operation" label="操作" width="300" show-overflow-tooltip />
      <el-table-column prop="duration" label="耗时" width="120" align="center">
        <template #default="{ row }">
          <span class="num-text">{{ row.duration }}ms</span>
        </template>
      </el-table-column>
      <el-table-column prop="resultStatus" label="结果" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.resultStatus === '成功' ? 'success' : 'danger'" size="small">
            {{ row.resultStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resultMsg" label="提示信息" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'text-danger': row.resultStatus === '失败' }">
            {{ row.resultMsg || (row.resultStatus === '成功' ? '-' : '') }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="130" show-overflow-tooltip />
      <el-table-column prop="createTime" label="操作时间" width="170">
        <template #default="{ row }">
          <span class="time-text">{{ row.createTime }}</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onPageChange"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { getOperationLogs } from "@/api/admin/operationLog.js";
import { logger } from "@/utils/logger.js";

const loading = ref(false);
const tableData = ref([]);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);

const filters = reactive({
  userName: "",
  operation: "",
  resultStatus: "",
  timeRange: null,
});

const fetchData = async () => {
  loading.value = true;
  try {
    const params = {
      page: page.value,
      size: pageSize.value,
    };
    if (filters.userName) params.userName = filters.userName;
    if (filters.operation) params.operation = filters.operation;
    if (filters.resultStatus) params.resultStatus = filters.resultStatus;
    if (filters.timeRange && filters.timeRange.length === 2) {
      params.startTime = filters.timeRange[0];
      params.endTime = filters.timeRange[1];
    }
    const res = await getOperationLogs(params);
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

const search = () => {
  page.value = 1;
  fetchData();
};

const onPageChange = () => {
  fetchData();
};

const reset = () => {
  filters.userName = "";
  filters.operation = "";
  filters.resultStatus = "";
  filters.timeRange = null;
  search();
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px;
  background: var(--color-bg);
  border-radius: 8px;
  border: 1px solid var(--color-border-light);
}

.filter-bar :deep(.el-date-editor) {
  width: 240px !important;
  flex: none;
}

.num-text {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.time-text {
  color: var(--color-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.text-muted {
  color: var(--color-text-tertiary);
}

.text-danger {
  color: var(--color-danger);
}

.pagination-wrap {
  display: flex;
  margin-top: 16px;
  padding-top: 12px;
}
</style>