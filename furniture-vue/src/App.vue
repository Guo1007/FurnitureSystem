<template>
  <!--
    Element Plus 改为按需引入后，不能再靠 app.use(ElementPlus, { locale }) 设置中文，
    改由 ConfigProvider 提供语言包（分页、日期面板等文案都依赖它）。
  -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { ElConfigProvider } from "element-plus";
import zhCn from "element-plus/dist/locale/zh-cn.mjs";
</script>

<style lang="scss">
@import "@/styles/views/app.scss";
</style>
