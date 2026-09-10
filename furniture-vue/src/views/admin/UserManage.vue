<template>
  <div class="manage-page">
    <h2 class="page-title">用户管理</h2>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchForm.phone"
        placeholder="手机号搜索"
        clearable
        style="width: 180px"
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model="searchForm.email"
        placeholder="邮箱搜索"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
      />
      <el-select
        v-model="searchForm.isAdmin"
        placeholder="用户类型"
        clearable
        style="width: 150px"
      >
        <el-option label="普通用户" :value="0" />
        <el-option label="管理员" :value="1" />
      </el-select>
      <el-button type="primary" @click="handleSearch"
        >搜索</el-button
      >
      <el-button @click="resetSearch">重置</el-button>
      <!-- 新增用户按钮：推到搜索栏最右侧 -->
      <el-button type="success" style="margin-left: auto" @click="handleAdd"
        >新增用户</el-button
      >
    </div>

    <el-table :data="userList" v-loading="loading" border>
      <el-table-column prop="userName" label="用户名" min-width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column
        prop="email"
        label="邮箱"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="address"
        label="收货地址"
        min-width="300"
        show-overflow-tooltip
      />
      <el-table-column prop="isAdmin" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isAdmin === 1 ? 'danger' : 'info'">
            {{ row.isAdmin === 1 ? "管理员" : "普通用户" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button type="warning" size="small" @click="handleResetPwd(row)"
            >重置密码</el-button
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
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="loadData"
      />
    </div>

    <!-- 编辑用户弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="编辑用户"
      width="450px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户ID">
          <el-input v-model="form.id" disabled />
        </el-form-item>

        <el-form-item label="用户名">
          <el-input v-model="form.userName" disabled />
        </el-form-item>

        <el-form-item label="手机号">
          <el-input v-model="form.phone" disabled />
        </el-form-item>

        <el-form-item label="邮箱">
          <el-input v-model="form.email" disabled />
        </el-form-item>

        <el-form-item label="用户类型" prop="isAdmin">
          <el-radio-group v-model="form.isAdmin">
            <el-radio :label="0">普通用户</el-radio>
            <el-radio :label="1">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit" :loading="submitting"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog
      v-model="pwdDialogVisible"
      title="重置密码"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px">
        <el-form-item label="用户名">
          <el-input :model-value="pwdForm.userName" disabled />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPwd" :loading="pwdSubmitting"
          >确定重置</el-button
        >
      </template>
    </el-dialog>

    <!-- 新增用户弹窗 -->
    <el-dialog
      v-model="addDialogVisible"
      title="新增用户"
      width="450px"
      :close-on-click-modal="false"
    >
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px">
        <el-form-item label="用户名" prop="userName">
          <el-input v-model="addForm.userName" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="addForm.phone" placeholder="手机号和邮箱至少填一项" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="addForm.email" placeholder="手机号和邮箱至少填一项" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="addForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="用户类型" prop="isAdmin">
          <el-radio-group v-model="addForm.isAdmin">
            <el-radio :label="0">普通用户</el-radio>
            <el-radio :label="1">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <p class="form-tip">
        提示：仅填写手机号的账号无法使用邮箱验证码登录 / 找回密码
      </p>

      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd" :loading="addSubmitting"
          >确定新增</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createUser,
  deleteUser,
  editUser,
  getUserList,
  resetPassword,
} from "@/api/admin/user.js";
import { logger } from "@/utils/logger.js";

const loading = ref(false);
const submitting = ref(false);
const userList = ref([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const dialogVisible = ref(false);
const pwdDialogVisible = ref(false);
const addDialogVisible = ref(false);
const formRef = ref(null);
const pwdFormRef = ref(null);
const addFormRef = ref(null);
const pwdSubmitting = ref(false);
const addSubmitting = ref(false);

const searchForm = ref({
  phone: "",
  email: "",
  isAdmin: null,
});

// 编辑表单
const form = reactive({
  id: null,
  userName: "",
  phone: "",
  email: "",
  isAdmin: 0,
});

// 重置密码表单
const pwdForm = reactive({
  id: null,
  userName: "",
  newPassword: "",
});

// 新增用户表单
const addForm = reactive({
  userName: "",
  phone: "",
  email: "",
  password: "",
  isAdmin: 0,
});

// 表单校验规则
const rules = {
  isAdmin: [{ required: true, message: "请选择用户类型", trigger: "change" }],
};

const pwdRules = {
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, message: "密码长度至少6位", trigger: "blur" },
  ],
};

// 新增用户表单校验规则（手机号/邮箱至少填写一项，整体校验在 submitAdd 里处理）
const addRules = {
  userName: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 2, max: 20, message: "用户名长度在 2 到 20 个字符", trigger: "blur" },
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" },
  ],
  email: [{ type: "email", message: "邮箱格式不正确", trigger: "blur" }],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码长度至少6位", trigger: "blur" },
  ],
  isAdmin: [{ required: true, message: "请选择用户类型", trigger: "change" }],
};

// 加载数据
const loadData = async () => {
  loading.value = true;
  try {
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      phone: searchForm.value.phone || undefined,
      email: searchForm.value.email || undefined,
      isAdmin: searchForm.value.isAdmin,
    };
    Object.keys(params).forEach((key) => {
      if (params[key] === null || params[key] === "") delete params[key];
    });

    const res = await getUserList(params);
    if (res.success || res.code === 200) {
      userList.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (error) {
    logger.error("加载失败:", error);
  } finally {
    loading.value = false;
  }
};

// 搜索与重置
const handleSearch = () => {
  currentPage.value = 1;
  loadData();
};

const resetSearch = () => {
  searchForm.value = { phone: "", email: "", isAdmin: null };
  handleSearch();
};

// 打开编辑弹窗
const handleEdit = (row) => {
  form.id = row.id;
  form.userName = row.userName;
  form.phone = row.phone;
  form.email = row.email || "";
  form.isAdmin = row.isAdmin;

  dialogVisible.value = true;
};

// 提交编辑
const submitEdit = async () => {
  if (!formRef.value) return;

  await formRef.value.validate(async (valid) => {
    if (!valid) return;

    try {
      await ElMessageBox.confirm(
        `确定修改用户类型为"${form.isAdmin === 1 ? "管理员" : "普通用户"}"吗？`,
        "确认修改",
        {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning",
        },
      );
    } catch {
      return;
    }

    submitting.value = true;
    try {
      const res = await editUser({ id: form.id, isAdmin: form.isAdmin });
      if (res.success || res.code === 200) {
        ElMessage.success("修改成功");
        dialogVisible.value = false;
        loadData();
      } else {
        ElMessage.error(res.msg || "修改失败");
      }
    } catch (error) {
      logger.error("修改异常:", error);
    } finally {
      submitting.value = false;
    }
  });
};

// 打开重置密码弹窗
const handleResetPwd = (row) => {
  pwdForm.id = row.id;
  pwdForm.userName = row.userName;
  pwdForm.newPassword = "";
  pwdDialogVisible.value = true;
};

// 提交重置密码
const submitResetPwd = async () => {
  if (!pwdFormRef.value) return;

  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return;

    try {
      await ElMessageBox.confirm(
        `确定重置用户"${pwdForm.userName}"的密码吗？`,
        "确认重置密码",
        {
          confirmButtonText: "确定重置",
          cancelButtonText: "取消",
          type: "warning",
        },
      );
    } catch {
      return;
    }

    pwdSubmitting.value = true;
    try {
      const res = await resetPassword({
        id: pwdForm.id,
        newPassword: pwdForm.newPassword.trim(),
      });
      if (res.success || res.code === 200) {
        ElMessage.success("密码重置成功");
        pwdDialogVisible.value = false;
      } else {
        ElMessage.error(res.msg || "重置失败");
      }
    } catch (error) {
      logger.error("重置密码异常:", error);
    } finally {
      pwdSubmitting.value = false;
    }
  });
};

// 打开新增用户弹窗
const handleAdd = () => {
  addForm.userName = "";
  addForm.phone = "";
  addForm.email = "";
  addForm.password = "";
  addForm.isAdmin = 0;
  addDialogVisible.value = true;
};

// 提交新增用户
const submitAdd = async () => {
  if (!addFormRef.value) return;

  if (!addForm.phone.trim() && !addForm.email.trim()) {
    ElMessage.warning("手机号和邮箱至少填写一项");
    return;
  }

  await addFormRef.value.validate(async (valid) => {
    if (!valid) return;

    addSubmitting.value = true;
    try {
      const res = await createUser({
        userName: addForm.userName.trim(),
        phone: addForm.phone.trim(),
        email: addForm.email.trim(),
        password: addForm.password,
        isAdmin: addForm.isAdmin,
      });
      if (res.success || res.code === 200) {
        ElMessage.success("新增用户成功");
        addDialogVisible.value = false;
        loadData();
      } else {
        ElMessage.error(res.msg || "新增失败");
      }
    } catch (error) {
      logger.error("新增用户异常:", error);
    } finally {
      addSubmitting.value = false;
    }
  });
};

// 删除用户
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定删除用户 "${row.userName}"（${row.phone}）吗？此操作不可恢复！`,
    "删除确认",
    {
      type: "error",
      confirmButtonText: "确定删除",
      cancelButtonText: "取消",
    },
  )
    .then(async () => {
      try {
        const res = await deleteUser(row.id);
        if (res.success || res.code === 200) {
          ElMessage.success("删除成功");
          loadData();
        } else {
          ElMessage.error(res.msg || "删除失败");
        }
      } catch (error) {
        logger.error("删除异常:", error);
      }
    })
    .catch(() => {});
};

const handleSizeChange = (val) => {
  currentPage.value = 1;
  loadData();
};

onMounted(loadData);
</script>

<style scoped lang="scss">
@import "@/styles/views/user-manage.scss";
</style>
