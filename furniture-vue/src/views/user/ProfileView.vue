<template>
  <div class="profile-page">
    <!-- Breadcrumb -->
    <div class="page-breadcrumb">
      <button class="breadcrumb-back" @click="goBack" title="返回">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
      </button>
      <router-link to="/">首页</router-link>
      <span>/</span>
      <span class="current">个人中心</span>
    </div>

    <div class="profile-container">
      <!-- 左侧：用户卡片 -->
      <div class="user-hero-card">
        <div class="hero-bg"></div>
        <div class="hero-content">
          <div class="avatar-wrapper">
            <img
              class="large-avatar"
              :src="imgUrl(userInfo.icon, '/images/default-avatar.png')"
              @error="(e) => (e.target.src = '/images/default-avatar.png')"
            />
            <div class="avatar-badge" :class="{ active: userInfo.hasPassword }">
              <el-icon v-if="userInfo.hasPassword" :size="14" color="#5b8c5a"
                ><Lock
              /></el-icon>
              <el-icon v-else :size="14" color="#c8843a"><Warning /></el-icon>
            </div>
            <el-tag
              v-if="userInfo.iconReviewStatus === 1"
              type="warning"
              size="small"
              style="position: absolute; bottom: -4px; left: 50%; transform: translateX(-50%); white-space: nowrap"
            >
              头像审核中
            </el-tag>
          </div>
          <div class="user-welcome">
            <h1 class="user-name">{{ userInfo.userName || "未知用户" }}</h1>
            <el-tag
              v-if="userInfo.nicknameReviewStatus === 1"
              type="warning"
              size="small"
              style="margin-top: 4px"
            >
              昵称审核中
            </el-tag>
            <el-tag
              v-if="userInfo.nicknameReviewStatus === 3"
              type="warning"
              size="small"
              style="margin-top: 4px"
            >
              昵称待复审
            </el-tag>
            <div class="meta-row">
              <el-icon><Phone /></el-icon>
              <span>{{ userInfo.phone || "未绑定手机" }}</span>
            </div>
            <div class="meta-row">
              <el-icon><Message /></el-icon>
              <span>{{ userInfo.email || "未绑定邮箱" }}</span>
            </div>
            <div class="meta-row" v-if="userInfo.createTime">
              <el-icon><Clock /></el-icon>
              <span>注册时间：{{ formatTime(userInfo.createTime) }}</span>
            </div>
            <div class="security-tag">
              <el-tag
                :type="userInfo.hasPassword ? 'success' : 'warning'"
                size="small"
                effect="dark"
              >
                {{ userInfo.hasPassword ? "已设置密码" : "建议设置密码" }}
              </el-tag>
            </div>
          </div>
          <el-button
            type="primary"
            plain
            @click="openEditDialog"
            class="edit-btn"
            >编辑资料</el-button
          >
        </div>
      </div>

      <!-- 右侧：功能网格 -->
      <div class="features-grid">
        <el-card
          shadow="hover"
          class="feature-card"
          @click="openPasswordDialog"
        >
          <div
            class="card-icon-box"
            :style="{
              background: userInfo.hasPassword ? '#f0f2f0' : '#faf8f5',
            }"
          >
            <el-icon
              :size="28"
              :color="userInfo.hasPassword ? '#2c2c2c' : '#999'"
              ><Lock
            /></el-icon>
          </div>
          <div class="card-info">
            <h3>{{ userInfo.hasPassword ? "修改密码" : "设置密码" }}</h3>
            <p>
              {{
                userInfo.hasPassword
                  ? "定期更换密码保障账户安全"
                  : "当前未设置密码，存在安全风险"
              }}
            </p>
          </div>
          <div class="card-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>

        <el-card shadow="hover" class="feature-card" @click="openEditDialog">
          <div class="card-icon-box" style="background: #f0f2f0">
            <el-icon :size="28" color="#2c2c2c"><User /></el-icon>
          </div>
          <div class="card-info">
            <h3>基本信息</h3>
            <p>管理昵称、收货地址及联系方式</p>
          </div>
          <div class="card-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>

        <el-card shadow="hover" class="feature-card" @click="goToOrders">
          <div class="card-icon-box" style="background: #f0f2f0">
            <el-icon :size="28" color="#2c2c2c"><ShoppingCart /></el-icon>
          </div>
          <div class="card-info">
            <h3>购买记录</h3>
            <p>查看历史订单及订单详情</p>
          </div>
          <div class="card-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>

        <el-card shadow="hover" class="feature-card" @click="goToFavorites">
          <div class="card-icon-box" style="background: #faf3eb">
            <el-icon :size="28" color="#b8753e"><Star /></el-icon>
          </div>
          <div class="card-info">
            <h3>我的收藏</h3>
            <p>查看已收藏的心仪家具</p>
          </div>
          <div class="card-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>

        <el-card shadow="hover" class="feature-card" @click="goToAddresses">
          <div class="card-icon-box" style="background: #eef2f7">
            <el-icon :size="28" color="#5a7fa0"><Location /></el-icon>
          </div>
          <div class="card-info">
            <h3>收货地址</h3>
            <p>管理常用收货地址</p>
          </div>
          <div class="card-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>

        <el-card shadow="hover" class="feature-card" @click="goToCoupons">
          <div class="card-icon-box" style="background: #faf3eb">
            <el-icon :size="28" color="#b8753e"><Tickets /></el-icon>
          </div>
          <div class="card-info">
            <h3>我的卡券</h3>
            <p>领取和使用优惠券</p>
          </div>
          <div class="card-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>

        <el-card shadow="hover" class="feature-card" @click="goToNotifications">
          <div class="card-icon-box" style="background: #f0f2f0">
            <el-icon :size="28" color="#2c2c2c"><Bell /></el-icon>
          </div>
          <div class="card-info">
            <h3>消息通知</h3>
            <p>查看系统消息与通知</p>
          </div>
          <div class="card-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>

        <el-card
          shadow="hover"
          class="feature-card logout-card"
          @click="handleLogout"
        >
          <div class="card-icon-box" style="background: #f5f5f5">
            <el-icon :size="28" color="#999"><SwitchButton /></el-icon>
          </div>
          <div class="card-info">
            <h3>退出</h3>
            <p>安全退出当前账号</p>
          </div>
          <div class="card-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>

        <el-card
          shadow="hover"
          class="feature-card danger-card"
          @click="handleDeactivate"
        >
          <div class="card-icon-box" style="background: #fdf0f0">
            <el-icon :size="28" color="#e35d5d"><Delete /></el-icon>
          </div>
          <div class="card-info">
            <h3>注销账号</h3>
            <p>永久注销当前账号，不可恢复</p>
          </div>
          <div class="card-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 编辑资料弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑个人资料"
      width="620px"
      :close-on-click-modal="false"
    >
      <div class="dialog-header-tip">
        完善您的个人信息，收货地址请在"收货地址"中管理。
      </div>
      <el-form
        :model="editForm"
        label-width="90px"
        style="margin-top: 20px"
        ref="editFormRef"
      >
        <el-form-item
          label="昵称"
          prop="userName"
          :rules="[{ required: true, message: '请输入昵称', trigger: 'blur' }]"
        >
          <el-input
            v-model="editForm.userName"
            placeholder="请输入您的昵称"
            maxlength="20"
            show-word-limit
          />
        </el-form-item>
        <el-form-item
          label="邮箱"
          prop="email"
          :rules="emailRules"
        >
          <div class="email-input-row">
            <el-input
              v-model="editForm.email"
              placeholder="绑定/更换邮箱地址"
              maxlength="100"
            />
            <el-button
              v-if="editForm.email !== userInfo.email"
              size="default"
              @click="sendEmailCode"
              :loading="sendingEmailCode"
            >发送验证码</el-button>
          </div>
        </el-form-item>
        <el-form-item
          v-if="editForm.email !== userInfo.email"
          label="新邮箱验证码"
          prop="emailCode"
          :rules="[{ required: true, message: '请输入新邮箱验证码', trigger: 'blur' }]"
        >
          <el-input
            v-model="editForm.emailCode"
            placeholder="请输入发送到新邮箱的验证码"
            maxlength="6"
          />
        </el-form-item>
        <el-form-item label="头像">
          <div class="avatar-upload-wrapper">
            <img
              class="preview-img"
              :src="imgUrl(editForm.icon, '/images/default-avatar.png')"
            />
            <div class="upload-action">
              <input
                type="file"
                accept="image/*"
                ref="fileInput"
                @change="onFileChange"
                style="display: none"
              />
              <el-button
                size="small"
                @click="fileInput.click()"
                :loading="uploading"
                >更换头像</el-button
              >
              <span v-if="uploading" class="upload-tip">上传中...</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitEdit(editFormRef)"
          :loading="submitting"
          >保存修改</el-button
        >
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog
      v-model="pwdDialogVisible"
      :title="userInfo.hasPassword ? '修改密码' : '设置密码'"
      width="450px"
      :close-on-click-modal="false"
    >
      <div
        class="dialog-header-tip"
        :class="!userInfo.hasPassword ? 'warning-tip' : ''"
      >
        {{
          userInfo.hasPassword
            ? "为了账户安全，建议每 3 个月更换一次密码。"
            : "为了保护账户安全，请务必设置登录密码。"
        }}
      </div>
      <el-form
        :model="pwdForm"
        label-width="90px"
        :rules="pwdRules"
        ref="pwdFormRef"
        style="margin-top: 20px"
      >
        <el-form-item
          v-if="userInfo.hasPassword"
          label="旧密码"
          prop="oldPassword"
        >
          <el-input
            v-model="pwdForm.oldPassword"
            type="password"
            placeholder="请输入当前密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            placeholder="6-20位字母或数字"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitPassword(pwdFormRef)"
          :loading="submitting"
          >确认提交</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  ArrowRight,
  Bell,
  Clock,
  Delete,
  Edit,
  Location,
  Lock,
  Message,
  Phone,
  ShoppingCart,
  Star,
  SwitchButton,
  Tickets,
  User,
  Warning,
} from "@element-plus/icons-vue";
import { useProfile } from "@/composables/useProfile.js";
import { imgUrl } from "@/utils/img.js";
import { formatTime } from "@/utils/format.js";
import { logger } from "@/utils/logger.js";
import { deactivateAccount, uploadAvatar } from "@/api/user.js";
import { ElMessage, ElMessageBox } from "element-plus";
import { useBackNavigation } from '@/composables/useBackNavigation.js';
import { useCartStore } from "@/stores/cart.js";
import { useUserStore } from "@/stores/user.js";

const editFormRef = ref(null);
const pwdFormRef = ref(null);
const fileInput = ref(null);
const router = useRouter();
const { goBack } = useBackNavigation();
const userStore = useUserStore();
const cartStore = useCartStore();

const {
  userInfo,
  editDialogVisible,
  pwdDialogVisible,
  submitting,
  uploading,
  editForm,
  pwdForm,
  pwdRules,
  emailRules,
  loadUserInfo,
  handleLogout,
  openEditDialog,
  submitEdit,
  openPasswordDialog,
  submitPassword,
  sendingEmailCode,
  sendEmailCode,
} = useProfile();

const goToOrders = () => router.push("/user/orders");
const goToFavorites = () => router.push("/user/favorites");
const goToCoupons = () => router.push("/user/coupons");
const goToNotifications = () => router.push("/notification");
const goToAddresses = () => router.push("/user/addresses");

const onFileChange = async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  uploading.value = true;
  try {
    const res = await uploadAvatar(file);
    if (res.success) {
      editForm.icon = res.data;
      ElMessage.success("头像上传成功，点击保存生效");
    } else ElMessage.error(res.msg || "上传失败");
  } catch (error) {
    logger.error("上传错误:", error);
  } finally {
    uploading.value = false;
  }
  e.target.value = "";
};

// 注销账号：二次确认后调用注销接口，成功后清理登录态并返回首页
const handleDeactivate = () => {
  ElMessageBox.confirm(
    "注销后您的账号将无法登录，历史订单与评价记录保留但不再关联账号，绑定的手机号/邮箱将被释放。此操作不可恢复，确定注销吗？",
    "注销账号",
    {
      confirmButtonText: "确定注销",
      cancelButtonText: "取消",
      type: "error",
    },
  )
    .then(async () => {
      try {
        const res = await deactivateAccount();
        if (res.success || res.code === 200) {
          ElMessage.success(res.msg || "账号已注销");
          userStore.logout();
          localStorage.removeItem("userName");
          localStorage.removeItem("userIcon");
          localStorage.removeItem("userEmail");
          sessionStorage.clear();
          cartStore.clearState();
          router.push("/");
        } else {
          ElMessage.error(res.msg || res.message || "注销失败");
        }
      } catch (error) {
        logger.error("注销账号异常:", error);
        ElMessage.error("注销失败，请稍后重试");
      }
    })
    .catch(() => {});
};

onMounted(() => {
  loadUserInfo();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/profile-view.scss";
</style>
