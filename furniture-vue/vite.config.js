import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import tailwindcss from "@tailwindcss/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { resolve } from "path";

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    // Element Plus 按需引入：此前 main.js 里 app.use(ElementPlus) 把整套组件（含后台用不到的
    // 表格/树/日期面板等）全部打进首屏包。改成只打包模板里真实用到的组件 + 对应 CSS。
    // dirs: [] 表示不做本地组件自动导入，只解析 Element Plus，避免意外改变现有引入行为。
    Components({
      dts: false,
      dirs: [],
      resolvers: [ElementPlusResolver({ importStyle: "css" })],
    }),
  ],
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: "modern-compiler",
        silenceDeprecations: ["import"],
      },
    },
  },
  server: {
    open: true,
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
  build: {
    // 关闭 gzip 体积报告的计算开销（只影响构建日志，不影响产物）
    reportCompressedSize: false,
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        // 此前所有依赖与业务代码被打进一个 1MB+ 的 index.js：
        // 首屏必须整包下载完才能启动，且任何一处改动都会让整个大文件缓存失效。
        // 这里按「变更频率」拆包：第三方库几乎不变，可长期命中浏览器缓存。
        manualChunks(id) {
          if (!id.includes("node_modules")) return;
          if (id.includes("element-plus")) return "vendor-element";
          if (id.includes("echarts") || id.includes("zrender")) return "vendor-echarts";
          if (id.includes("langchain4j") || id.includes("ai/")) return "vendor-ai";
          if (
            id.includes("/vue/") ||
            id.includes("/vue-router/") ||
            id.includes("/pinia/") ||
            id.includes("@vue")
          ) {
            return "vendor-vue";
          }
          return "vendor-common";
        },
      },
    },
  },
});
