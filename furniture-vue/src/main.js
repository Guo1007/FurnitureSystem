import { createApp } from "vue";
import { createPinia } from "pinia";
import { ElLoading, ElMessage } from "element-plus";
// Element Plus 已改为按需引入（见 vite.config.js 的 unplugin-vue-components）。
// 这里不再 app.use(ElementPlus) 全量注册，只补两块「不在模板里出现、按需插件扫不到」的样式：
// ElMessage / ElMessageBox / v-loading 主要在 .js 里以函数或指令形式调用。
import "element-plus/theme-chalk/el-message.css";
import "element-plus/theme-chalk/el-message-box.css";
import "element-plus/theme-chalk/el-loading.css";
// EP 主题覆盖必须在 EP 组件样式之后：把出厂蓝换成与前台一致的暖棕体系
import "@/styles/element-theme.css";
import "./index.css";
import "@/styles/global.css";
// 管理端页面骨架统一增强（标题/分页/弹窗等非 scoped 收口）
import "@/styles/admin-common.css";
import App from "./App.vue";
import router from "./router";
import "@/styles/views/auth.scss";
import "@/styles/views/furniture.scss";
import "@/styles/responsive.scss";

// 全局 toast 去重：相同文案 3 秒内只显示一次，避免并发请求失败时错误提示堆积
const _lastToastAt = {};
const _dedupeToast = (fn) => (message, ...rest) => {
  const key =
    typeof message === "string" ? message : JSON.stringify(message) || "toast";
  const now = Date.now();
  if (_lastToastAt[key] && now - _lastToastAt[key] < 3000) {
    return;
  }
  _lastToastAt[key] = now;
  return fn(message, ...rest);
};
ElMessage.error = _dedupeToast(ElMessage.error);
ElMessage.warning = _dedupeToast(ElMessage.warning);

const app = createApp(App);

// v-loading 指令：模板里的 v-loading 由按需插件解析，这里再全局注册一次做兜底，
// 防止个别文件（如通过 JSX / 动态渲染）拿不到指令时静默失效。
app.directive("loading", ElLoading.directive);

app.use(createPinia());
app.use(router);

app.mount("#app");
