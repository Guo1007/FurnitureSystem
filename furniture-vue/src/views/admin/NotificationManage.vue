<template>
  <div class="manage-page">
    <h2 class="page-title">通知管理</h2>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-select
        v-model="filterType"
        placeholder="筛选类型"
        clearable
        style="width: 150px"
        @change="loadData"
      >
        <el-option label="全部" value="" />
        <el-option label="系统通知" value="system" />
        <el-option label="订单通知" value="order" />
        <el-option label="回复通知" value="comment_reply" />
        <el-option label="评价拒绝" value="comment_reject" />
        <el-option label="追评拒绝" value="append_reject" />
        <el-option label="回复拒绝" value="reply_reject" />
        <el-option label="资料审核" value="profile_review" />
      </el-select>
      <el-button type="primary" @click="openAddDialog">发布通知</el-button>
      <el-button
        type="danger"
        :disabled="selectedNotifications.length === 0"
        @click="handleBatchDelete"
      >
        批量删除
        <span v-if="selectedNotifications.length > 0"
          >({{ selectedNotifications.length }})</span
        >
      </el-button>
    </div>

    <!-- 通知列表 -->
    <el-table
      :data="list"
      v-loading="loading"
      border
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="45" />
      <!-- <el-table-column prop="id" label="ID" width="70"/> -->
      <el-table-column
        prop="title"
        label="标题"
        min-width="90"
        show-overflow-tooltip
      />
      <el-table-column
        prop="content"
        label="内容"
        min-width="135"
        show-overflow-tooltip
      />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag
            :type="
              row.type === 'system'
                ? ''
                : row.type === 'order'
                  ? 'warning'
                  : row.type === 'comment_reply'
                    ? 'success'
                    : 'danger'
            "
            size="small"
          >
            {{
              row.type === "system"
                ? "系统"
                : row.type === "order"
                  ? "订单"
                  : row.type === "comment_reply"
                    ? "回复"
                    : row.type === "comment_reject"
                      ? "评价拒绝"
                      : row.type === "append_reject"
                        ? "追评拒绝"
                        : row.type === "reply_reject"
                          ? "回复拒绝"
                          : "资料审核"
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="接收对象" width="200">
        <template #default="{ row }">
          <span>{{
            row.userId ? row.userName || "指定用户" : "全部用户"
          }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="发送时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="handleEdit(row)"
            >编辑</el-button
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
        v-model:current-page="current"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onSizeChange"
        @current-change="loadData"
      />
    </div>

    <!-- 发布/编辑通知弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editMode ? '编辑通知' : '发布通知'"
      width="550px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="接收对象" prop="userId">
          <el-radio-group v-model="sendScope">
            <el-radio :value="0">所有用户</el-radio>
            <el-radio :value="1">指定用户</el-radio>
          </el-radio-group>
          <el-select
            v-if="sendScope === 1"
            v-model="form.userId"
            placeholder="请搜索并选择用户"
            filterable
            style="width: 280px; margin-top: 8px"
            :filter-method="searchUsers"
            @focus="loadUsers"
          >
            <el-option
              v-for="u in userList"
              :key="u.id"
              :label="`${u.userName} (${u.email})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="通知类型" prop="type">
          <el-select v-model="form.type" style="width: 200px">
            <el-option label="系统通知" value="system" />
            <el-option label="订单通知" value="order" />
            <el-option label="回复通知" value="comment_reply" />
            <el-option label="评价拒绝" value="comment_reject" />
            <el-option label="追评拒绝" value="append_reject" />
            <el-option label="回复拒绝" value="reply_reject" />
        <el-option label="资料审核" value="profile_review" />
          </el-select>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="通知标题"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            placeholder="通知内容"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="邮件通知">
          <el-checkbox v-model="form.sendEmail"
            >同时发送邮件通知给目标用户</el-checkbox
          >
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">
          {{ editMode ? "保存修改" : "发送通知" }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { formatTime } from "@/utils/format.js";
import { logger } from "@/utils/logger.js";
import {
  batchDeleteNotifications,
  deleteNotification,
  getAdminNotificationList,
  sendNotification,
  updateNotification,
} from "@/api/admin/notification.js";
import { getSimpleUserList } from "@/api/admin/user.js";

const loading = ref(false);
const submitting = ref(false);
const list = ref([]);
const current = ref(1);
const pageSize = ref(10);
const total = ref(0);
const dialogVisible = ref(false);
const editMode = ref(false);
const editId = ref(null);
const sendScope = ref(0);
const formRef = ref(null);

const filterType = ref("");
const userList = ref([]);
const selectedNotifications = ref([]);

const handleSelectionChange = (rows) => {
  selectedNotifications.value = rows;
};

const handleBatchDelete = async () => {
  const ids = selectedNotifications.value.map((r) => r.id);
  if (ids.length === 0) return;
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${ids.length} 条通知吗？`,
      "批量删除",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    const res = await batchDeleteNotifications(ids);
    if (res.success || res.code === 200) {
      ElMessage.success(`已删除 ${ids.length} 条通知`);
      selectedNotifications.value = [];
      loadData();
    } else {
      ElMessage.error(res.msg || "批量删除失败");
    }
  } catch (error) {
    if (error !== "cancel") logger.error("批量删除异常:", error);
  }
};

const form = reactive({
  userId: null,
  title: "",
  content: "",
  type: "system",
  sendEmail: false,
});

const rules = {
  title: [{ required: true, message: "请输入通知标题", trigger: "blur" }],
  content: [{ required: true, message: "请输入通知内容", trigger: "blur" }],
};

const loadData = async () => {
  loading.value = true;
  try {
    const res = await getAdminNotificationList(
      current.value,
      pageSize.value,
      filterType.value,
    );
    if (res.success && res.data) {
      list.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (e) {
    logger.error(e);
  } finally {
    loading.value = false;
  }
};

const loadUsers = async (keyword = "") => {
  try {
    const res = await getSimpleUserList(keyword);
    if (res.success && res.data) {
      userList.value = res.data || [];
    }
  } catch (e) {
    logger.error(e);
  }
};

const searchUsers = (kw) => {
  loadUsers(kw);
};

const resetForm = () => {
  form.title = "";
  form.content = "";
  form.type = "system";
  form.userId = null;
  form.sendEmail = false;
  sendScope.value = 0;
  editMode.value = false;
  editId.value = null;
};

const openAddDialog = () => {
  resetForm();
  dialogVisible.value = true;
};

const handleEdit = (row) => {
  editMode.value = true;
  editId.value = row.id;
  form.title = row.title;
  form.content = row.content;
  form.type = row.type || "system";
  form.userId = row.userId;
  sendScope.value = row.userId ? 1 : 0;
  dialogVisible.value = true;
  if (row.userId) {
    loadUsers();
  }
};

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  const payload = {
    title: form.title,
    content: form.content,
    type: form.type,
    userId: sendScope.value === 0 ? null : Number(form.userId),
    sendEmail: form.sendEmail,
  };

  submitting.value = true;
  try {
    let res;
    if (editMode.value && editId.value) {
      res = await updateNotification(editId.value, payload);
    } else {
      res = await sendNotification(payload);
    }
    if (res.success) {
      ElMessage.success(editMode.value ? "修改成功" : "发送成功");
      dialogVisible.value = false;
      loadData();
    }
  } catch (e) {
    logger.error(e);
  } finally {
    submitting.value = false;
  }
};

const handleDelete = (row) => {
  ElMessageBox.confirm("确定要删除这条通知吗？", "删除确认", {
    type: "warning",
    confirmButtonText: "确定删除",
    cancelButtonText: "取消",
  })
    .then(async () => {
      const res = await deleteNotification(row.id);
      if (res.success) {
        ElMessage.success("删除成功");
        loadData();
      }
    })
    .catch(() => {});
};

const onSizeChange = () => {
  current.value = 1;
  loadData();
};

onMounted(loadData);
</script>

<style scoped lang="scss">
@import "@/styles/views/notification-manage.scss";
</style>
