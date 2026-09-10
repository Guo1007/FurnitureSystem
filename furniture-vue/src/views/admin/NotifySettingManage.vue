<template>
  <div class="manage-page">
    <h2 class="page-title">邮件配置</h2>

    <el-card v-loading="loading">
      <!-- 列表展示：每个功能一行，独立配置邮件开关与接收管理员 -->
      <el-table :data="configs" border>
        <el-table-column label="功能" min-width="200">
          <template #default="{ row }">
            <div class="func-name">{{ row.label }}</div>
            <div class="func-desc">{{ row.desc }}</div>
          </template>
        </el-table-column>

        <el-table-column label="邮件通知" width="110" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" size="large" />
          </template>
        </el-table-column>

        <el-table-column label="接收管理员" min-width="280">
          <template #default="{ row }">
            <el-select
              v-model="row.adminIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              :disabled="!row.enabled"
              placeholder="选择接收通知的管理员"
              style="width: 100%"
            >
              <el-option
                v-for="a in admins"
                :key="a.id"
                :label="a.userName + (a.email ? '（' + a.email + '）' : '')"
                :value="a.id"
                :disabled="!a.email"
              />
            </el-select>
          </template>
        </el-table-column>
      </el-table>

      <p v-if="admins.length === 0" class="empty-tip">
        暂无可选择的管理员（需要 is_admin=1 的账号）
      </p>

      <div class="save-bar">
        <el-button type="primary" :loading="saving" @click="handleSave">
          保存设置
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { logger } from "@/utils/logger.js";
import { getNotifySetting, saveNotifySetting } from "@/api/admin/notifySetting.js";

const loading = ref(false);
const saving = ref(false);
const admins = ref([]);

// 各功能通知配置：独立开关 + 独立接收管理员
const configs = reactive([
  {
    notifyType: "new_order",
    label: "新订单通知",
    desc: "用户提交订单后，邮件通知管理员及时处理发货",
    enabled: false,
    adminIds: [],
  },
  {
    notifyType: "refund",
    label: "售后退款通知",
    desc: "用户发起退款 / 售后申请时，邮件通知管理员及时审核",
    enabled: false,
    adminIds: [],
  },
  {
    notifyType: "stock_alert",
    label: "库存预警",
    desc: "每天 10:00、18:00 检查商品库存，不足时邮件提醒补货",
    enabled: false,
    adminIds: [],
  },
]);

const loadSetting = async () => {
  loading.value = true;
  try {
    const res = await getNotifySetting();
    if (res.success || res.code === 200) {
      admins.value = res.data.admins || [];
      const remote = res.data.configs || [];
      configs.forEach((cfg) => {
        const found = remote.find((r) => r.notifyType === cfg.notifyType);
        if (found) {
          cfg.enabled = !!found.enabled;
          cfg.adminIds = found.adminIds || [];
        }
      });
    }
  } catch (e) {
    logger.error(e);
  } finally {
    loading.value = false;
  }
};

const handleSave = async () => {
  for (const cfg of configs) {
    if (cfg.enabled && cfg.adminIds.length === 0) {
      ElMessage.warning(`「${cfg.label}」开启后请至少选择一位接收管理员`);
      return;
    }
  }
  saving.value = true;
  try {
    for (const cfg of configs) {
      const res = await saveNotifySetting({
        notifyType: cfg.notifyType,
        enabled: cfg.enabled,
        adminIds: cfg.adminIds,
      });
      if (!(res.success || res.code === 200)) {
        ElMessage.error(res.msg || res.message || `「${cfg.label}」保存失败`);
        return;
      }
    }
    ElMessage.success("保存成功");
    loadSetting();
  } catch (e) {
    logger.error(e);
    ElMessage.error("保存失败");
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadSetting();
});
</script>

<style scoped lang="scss">
@import "@/styles/views/notify-setting-manage.scss";
</style>
