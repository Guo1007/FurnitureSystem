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
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <div class="op-cell">
            <el-button
              v-if="row.status === 1"
              type="primary"
              size="small"
              @click="handleShip(row)"
            >
              发货
            </el-button>
            <el-button type="success" size="small" plain @click="handlePayments(row)"
              >支付流水</el-button
            >
            <el-button type="danger" size="small" @click="handleDelete(row.id)"
              >删除</el-button
            >
          </div>
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
    <el-dialog v-model="dialogVisible" title="🛒 商品明细" width="920px" top="6vh">
      <template v-if="currentOrder">
        <el-descriptions :column="4" border size="small" class="order-desc">
          <el-descriptions-item label="订单号">{{ currentOrder.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="getStatusType(currentOrder.status)">{{
              getStatusText(currentOrder.status)
            }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="商品件数">
            <el-tag type="primary" size="small"
              >{{ currentOrder.itemList?.length || 0 }} 件</el-tag
            >
          </el-descriptions-item>
          <el-descriptions-item label="订单总额"
            ><b class="price-text">¥{{ currentOrder.totalPrice }}</b></el-descriptions-item
          >
          <el-descriptions-item label="收货人">{{ currentOrder.consignee }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ currentOrder.phone }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ currentOrder.createTime }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="4">{{ currentOrder.address }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <div class="dialog-subtitle">
        商品明细
        <span class="subtitle-count">共 {{ currentOrderItems.length }} 件</span>
      </div>
      <el-table :data="currentOrderItems" border size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="furnitureName" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="规格" width="170">
          <template #default="{ row }">
            <el-tag v-if="row.skuSpec" type="info" size="small" effect="plain">{{ row.skuSpec }}</el-tag>
            <span v-else style="color:#999">默认规格</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }"> ¥{{ row.price }} </template>
        </el-table-column>
        <el-table-column label="数量" width="80" align="center">
          <template #default="{ row }"> ×{{ row.quantity }} </template>
        </el-table-column>
        <el-table-column label="小计" width="120" align="right">
          <template #default="{ row }"><b class="price-text">¥{{ row.itemTotalPrice }}</b></template>
        </el-table-column>
      </el-table>
      <div class="dialog-total">
        合计：<b class="price-text">¥{{ itemsTotal }}</b>（共 {{ currentOrderItems.length }} 件商品）
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 支付流水弹窗 -->
    <el-dialog v-model="paymentDialogVisible" title="💳 支付流水" width="960px" top="6vh">
      <el-descriptions v-if="currentPayments.length" :column="3" border size="small" class="order-desc">
        <el-descriptions-item label="关联订单">#{{ currentPayments[0]?.orderId }}</el-descriptions-item>
        <el-descriptions-item label="支付渠道">
          <el-tag size="small" type="primary">支付宝</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="流水条数">{{ currentPayments.length }} 条</el-descriptions-item>
      </el-descriptions>
      <div class="dialog-subtitle" v-if="currentPayments.length">
        支付记录
        <span class="subtitle-count">共 {{ currentPayments.length }} 笔</span>
      </div>
      <el-table :data="currentPayments" border size="small" v-loading="paymentLoading">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="payNo" label="商户单号" min-width="230" show-overflow-tooltip />
        <el-table-column label="渠道" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.channel === 'alipay'" size="small" type="primary">支付宝</el-tag>
            <el-tag v-else size="small">{{ row.channel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付金额" width="120" align="right">
          <template #default="{ row }"><b class="price-text">¥{{ row.totalAmount }}</b></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">已支付</el-tag>
            <el-tag v-else-if="row.status === 0" type="warning" size="small">待支付</el-tag>
            <el-tag v-else type="info" size="small">已关闭</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tradeNo" label="支付宝交易号" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.tradeNo">{{ row.tradeNo }}</span>
            <el-tag v-else type="info" size="small">未回填</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" width="180">
          <template #default="{ row }">
            <span v-if="row.payTime">{{ row.payTime }}</span>
            <span v-else style="color:#999">-</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无支付流水记录" :image-size="60" />
        </template>
      </el-table>
      <template #footer>
        <el-button type="primary" @click="paymentDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  batchDeleteOrders,
  deleteOrder,
  exportOrders,
  getOrderList,
  getOrderPayments,
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
const currentOrder = ref(null); // 当前查看的商品明细所属订单（用于展示订单概要）
const pendingShipCount = ref(0);
const paymentDialogVisible = ref(false);
const paymentLoading = ref(false);
const currentPayments = ref([]);

// 商品明细合计金额
const itemsTotal = computed(() =>
  (currentOrderItems.value || []).reduce(
    (sum, item) => sum + Number(item.itemTotalPrice || 0),
    0,
  ),
);

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
  currentOrder.value = row;
  currentOrderItems.value = row.itemList || [];
  dialogVisible.value = true;
};

// 查看订单支付流水
const handlePayments = async (row) => {
  paymentDialogVisible.value = true;
  paymentLoading.value = true;
  currentPayments.value = [];
  try {
    const res = await getOrderPayments(row.id);
    if (res.success || res.code === 200) {
      currentPayments.value = res.data || [];
    } else {
      ElMessage.error(res.msg || "获取支付流水失败");
    }
  } catch (e) {
    logger.error("获取支付流水异常:", e);
    ElMessage.error("获取支付流水失败");
  } finally {
    paymentLoading.value = false;
  }
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

.op-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;

  // 覆盖 Element 按钮之间的默认外边距，避免换行错乱
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.price-text {
  color: var(--el-color-danger);
}

.order-desc {
  margin-bottom: 14px;
}

.dialog-subtitle {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);

  .subtitle-count {
    font-weight: 400;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.dialog-total {
  margin-top: 12px;
  text-align: right;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
</style>
