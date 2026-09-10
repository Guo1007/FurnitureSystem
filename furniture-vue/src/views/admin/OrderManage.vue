<template>
  <div class="manage-page">
    <h2 class="page-title">订单管理</h2>

    <!-- 待发货提醒 -->
    <el-alert
      v-if="pendingShipCount > 0"
      type="warning"
      show-icon
      :closable="false"
      class="pending-alert"
    >
      <template #title>
        <span
          >有
          <strong>{{ pendingShipCount }}</strong>
          个已支付订单待发货，请及时处理</span
        >
        <el-button
          type="warning"
          size="small"
          plain
          style="margin-left: 12px"
          @click="filterPendingShip"
          >查看待发货订单</el-button
        >
      </template>
    </el-alert>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-select
        v-model="searchForm.status"
        placeholder="订单状态"
        clearable
        style="width: 150px"
      >
        <el-option label="待支付" :value="0" />
        <el-option label="已支付" :value="1" />
        <el-option label="已发货" :value="2" />
        <el-option label="已完成" :value="3" />
        <el-option label="已取消" :value="4" />
        <el-option label="已评价" :value="5" />
        <el-option label="申请退款中" :value="6" />
        <el-option label="退款审核中" :value="7" />
        <el-option label="已退款" :value="8" />
      </el-select>
      <el-input
        v-model="searchForm.phone"
        placeholder="收货手机号"
        clearable
        style="width: 150px"
      />
      <el-input
        v-model="searchForm.consignee"
        placeholder="收货人姓名"
        clearable
        style="width: 150px"
      />
      <el-button type="primary" @click="handleSearch"
        >搜索</el-button
      >
      <el-button @click="resetSearch">重置</el-button>
      <el-button type="success" @click="handleExport"
        >导出 Excel</el-button
      >
      <el-button
        type="danger"
        :disabled="selectedOrders.length === 0"
        @click="handleBatchDelete"
      >
        批量删除
        <span v-if="selectedOrders.length > 0"
          >({{ selectedOrders.length }})</span
        >
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table
      :data="orderList"
      v-loading="loading"
      border
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column prop="id" label="订单号" min-width="190" />
      <el-table-column prop="consignee" label="收货人" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column
        prop="address"
        label="收货地址"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column prop="totalPrice" label="金额" width="120">
        <template #default="{ row }">
          <span style="color: #d95a5a; font-weight: 600"
            >¥{{ row.totalPrice }}</span
          >
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ getStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" width="150">
        <template #default="{ row }">
          <span v-if="row.remark">{{ row.remark }}</span>
          <el-tag v-else type="info" size="small">无备注</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="商品详情" width="180">
        <template #default="{ row }">
          <el-tag type="primary" size="small">
            共 {{ row.itemList?.length || 0 }} 件
          </el-tag>
          <el-button
            size="small"
            type="primary"
            text
            style="margin-left: 10px"
            @click="handleViewItems(row)"
          >
            查看明细
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 1"
            type="primary"
            size="small"
            @click="handleShip(row)"
          >
            发货
          </el-button>
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
        @current-change="loadData"
      />
    </div>

    <!-- 商品明细弹窗 -->
    <el-dialog v-model="dialogVisible" title="🛒 商品明细" width="750px">
      <el-table :data="currentOrderItems" border size="small">
        <el-table-column
          prop="furnitureName"
          label="商品名称"
          min-width="130"
        />
        <el-table-column label="规格" width="160">
          <template #default="{ row }">
            <span v-if="row.skuSpec" style="font-size: 12px; color: #666">{{
              row.skuSpec
            }}</span>
            <el-tag v-else type="info" size="small">默认规格</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="price" label="单价" width="90">
          <template #default="{ row }"> ¥{{ row.price }} </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="70" />
        <el-table-column label="小计" width="100">
          <template #default="{ row }"> ¥{{ row.itemTotalPrice }} </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  batchDeleteOrders,
  deleteOrder,
  exportOrders,
  getOrderList,
  getPendingOrderCount,
  shipOrder,
} from "@/api/admin/order.js";
import { logger } from "@/utils/logger.js";

const loading = ref(false);
const orderList = ref([]);
const selectedOrders = ref([]);
const currentPage = ref(1);
const pageSize = ref(10);

const handleExport = async () => {
  try {
    const blob = await exportOrders();
    const url = window.URL.createObjectURL(
      new Blob([blob], { type: "text/csv;charset=UTF-8" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "orders.csv";
    a.click();
    window.URL.revokeObjectURL(url);
    ElMessage.success("导出成功");
  } catch (e) {
    logger.error("导出失败，请重试:", e);
  }
};
const total = ref(0);

const dialogVisible = ref(false);
const currentOrderItems = ref([]);
const pendingShipCount = ref(0);

const searchForm = ref({
  userId: null,
  status: null,
  phone: "",
  consignee: "",
});

const statusMap = {
  0: { text: "待支付", type: "warning" },
  1: { text: "已支付", type: "success" },
  2: { text: "已发货", type: "primary" },
  3: { text: "已完成", type: "info" },
  4: { text: "已取消", type: "danger" },
  5: { text: "已评价", type: "success" },
  6: { text: "申请退款中", type: "warning" },
  7: { text: "退款审核中", type: "primary" },
  8: { text: "已退款", type: "info" },
};

const getStatusText = (status) => statusMap[status]?.text || "未知";
const getStatusType = (status) => statusMap[status]?.type || "info";

const loadData = async () => {
  loading.value = true;
  try {
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      userId: searchForm.value.userId || undefined,
      status: searchForm.value.status,
      phone: searchForm.value.phone || undefined,
      consignee: searchForm.value.consignee || undefined,
    };
    Object.keys(params).forEach((key) => {
      if (params[key] === null || params[key] === "") delete params[key];
    });

    const res = await getOrderList(params);
    if (res.success || res.code === 200) {
      orderList.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (error) {
    logger.error("加载失败:", error);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  loadData();
};

const resetSearch = () => {
  searchForm.value = { userId: null, status: null, phone: "", consignee: "" };
  handleSearch();
};

// 发货处理
const handleShip = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定对订单 "${row.id}" 进行发货吗？`,
      "确认发货",
      {
        confirmButtonText: "确定发货",
        cancelButtonText: "取消",
        type: "warning",
      },
    );

    const res = await shipOrder(row.id);
    if (res.success || res.code === 200) {
      ElMessage.success("发货成功");
      loadData();
      fetchPendingCount();
    } else {
      ElMessage.error(res.msg || "发货失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      logger.error("发货异常:", error);
    }
  }
};

const handleSelectionChange = (rows) => {
  selectedOrders.value = rows;
};

const handleBatchDelete = async () => {
  const ids = selectedOrders.value.map((r) => r.id);
  if (ids.length === 0) return;
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${ids.length} 个订单吗？此操作为软删除。`,
      "批量删除",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    const res = await batchDeleteOrders(ids);
    if (res.success || res.code === 200) {
      ElMessage.success(`已删除 ${ids.length} 个订单`);
      selectedOrders.value = [];
      loadData();
    } else {
      ElMessage.error(res.msg || "批量删除失败");
    }
  } catch (error) {
    if (error !== "cancel") logger.error("批量删除异常:", error);
  }
};

const handleDelete = async (orderId) => {
  try {
    await ElMessageBox.confirm(
      `确定删除订单 "${orderId}" 吗？（该操作不可逆）`,
      "确认删除",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    const res = await deleteOrder(orderId);
    if (res.success || res.code === 200) {
      ElMessage.success("删除成功");
      loadData();
    } else {
      ElMessage.error(res.msg || "删除失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      logger.error("删除订单异常:", error);
    }
  }
};

const handleViewItems = (row) => {
  currentOrderItems.value = row.itemList || [];
  dialogVisible.value = true;
};

const handleSizeChange = (val) => {
  currentPage.value = 1;
  loadData();
};

const fetchPendingCount = async () => {
  try {
    const res = await getPendingOrderCount();
    if (res.success || res.code === 200) {
      pendingShipCount.value = res.data?.pendingShipCount || 0;
    }
  } catch (e) {
    /* ignore */
  }
};

const filterPendingShip = () => {
  searchForm.value.status = 1; // 已支付
  handleSearch();
};

onMounted(() => {
  loadData();
  fetchPendingCount();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/order-manage.scss";
</style>
