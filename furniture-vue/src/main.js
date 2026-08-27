import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus, { ElMessage } from "element-plus";
import "element-plus/dist/index.css";
import "./index.css";
import "@/styles/global.css";
import App from "./App.vue";
import router from "./router";
import zhCn from "element-plus/dist/locale/zh-cn.mjs";
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

app.use(ElementPlus, {
  locale: zhCn,
});

app.use(createPinia());
app.use(router);

app.mount("#app");
