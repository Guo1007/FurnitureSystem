import axios from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";
import { useUserStore } from "@/stores/user";
import { useCartStore } from "@/stores/cart";

const service = axios.create({
  baseURL: "/api",
  timeout: 15000,
});

// 错误去重：同类型错误 2 秒内只弹一次 toast
const _lastErrorTime = {};

const _shouldShowError = (key) => {
  const now = Date.now();
  if (_lastErrorTime[key] && now - _lastErrorTime[key] < 2000) {
    return false;
  }
  _lastErrorTime[key] = now;
  return true;
};

// 统一处理 401（登录态失效）：清响应式登录态；仅需登录页面跳转登录，游客可浏览页面不跳转
// 返回当前页面是否需要登录（true=需登录，调用方据此决定是否弹错误提示）
const handleUnauthorized = () => {
  const userStore = useUserStore();
  userStore.logout(); // 清 token + userInfo + localStorage，导航栏立即变未登录
  useCartStore().clearState(); // 清空购物车角标
  const isProtected = !!router.currentRoute.value.meta?.requiresAuth;
  if (isProtected) {
    router.push("/login");
  }
  return isProtected;
};

service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers["Authorization"] = "Bearer " + token;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

service.interceptors.response.use(
  (response) => {
    if (
      response.config.responseType === "blob" ||
      response.data instanceof Blob
    ) {
      return response.data;
    }
    const res = response.data;
    const isSuccess =
      res.code === 200 || res.code === "200" || res.success === true;

    if (!isSuccess) {
      // 401：登录态失效（token 过期/Redis 登录态消失），清登录态并按页面类型处理
      if (res.code === 401 || res.code === "401") {
        const isProtected = handleUnauthorized();
        if (isProtected && _shouldShowError("401")) {
          ElMessage.error(res.msg || "登录已过期，请重新登录");
        }
        return Promise.reject(res);
      }
      // 其他业务错误：不弹 toast，不抛异常，正常返回 res
      // 由组件 else 分支用 res.msg 展示，一个地方弹一次
      return res;
    }
    return res;
  },
  (error) => {
    // 网络/HTTP 层错误：拦截器统一弹 toast（只弹一次）
    // 组件 catch 块不要再弹 toast
    let message = "网络异常，请稍后重试";
    if (error.response) {
      const status = error.response.status;
      if (status === 401) {
        // 401：登录态失效。读取后端 msg，区分「登录已过期」与「被顶下线」；
        // 无论是否受保护页面都提前 return，避免落入下方通用“网络异常”提示
        const msg = error.response.data?.msg || "登录已过期，请重新登录";
        handleUnauthorized();
        if (_shouldShowError("401")) {
          ElMessage.error(msg);
        }
        return Promise.reject(error);
      } else if (status === 404) {
        message = "页面或资源不存在";
      } else if (status === 500) {
        message = "服务器开小差了，请稍后重试";
      } else {
        message = error.response.data?.msg || "网络连接异常，请稍后重试";
      }
    } else {
      if (error.message?.includes("timeout")) {
        message = "请求超时，请稍后重试";
      } else if (error.message?.includes("Network")) {
        message = "网络连接失败，请检查网络";
      } else {
        message = "网络异常，请稍后重试";
      }
    }
    if (_shouldShowError(message)) {
      ElMessage.error(message);
    }
    return Promise.reject(error);
  },
);

export default service;
