<template>
  <div class="manage-page">
    <div class="cv-topbar">
      <div>
        <h2 class="page-title">首页内容管理</h2>
        <p class="page-sub">配置前台各版块内容，按分组统一保存后立即同步到对应页面。</p>
      </div>
      <el-button type="primary" :loading="saving" @click="saveGroup">
        保存当前分组
      </el-button>
    </div>

    <el-tabs v-model="activeGroup" class="cv-tabs">
      <!-- 轮播图 -->
      <el-tab-pane label="轮播图" name="carousel">
        <el-empty
          v-if="!carouselItems.length"
          description="暂无轮播图数据"
          :image-size="72"
        />
        <template v-else>
          <div class="cv-preview">
            <div class="cv-preview-hd">
              <span class="cv-preview-title">前台实时预览</span>
              <span class="cv-preview-count">{{ carouselSlides.length }} 张</span>
            </div>
            <div class="cv-preview-body">
              <div
                class="cv-mini-track"
                :style="{ transform: `translateX(-${previewIndex * 100}%)` }"
              >
                <div
                  class="cv-mini-slide"
                  v-for="(s, i) in carouselSlides"
                  :key="i"
                  :style="{ backgroundColor: s.bg }"
                >
                  <span class="cv-mini-emoji">{{ s.emoji }}</span>
                  <div class="cv-mini-text">
                    <div class="cv-mini-tag">{{ s.tag }}</div>
                    <div class="cv-mini-title">{{ s.title }}</div>
                    <div class="cv-mini-desc">{{ s.desc }}</div>
                  </div>
                </div>
              </div>
              <div class="cv-mini-dots">
                <span
                  v-for="(s, i) in carouselSlides"
                  :key="i"
                  :class="{ on: i === previewIndex }"
                  @click="previewIndex = i"
                ></span>
              </div>
            </div>
          </div>

          <div class="cv-list">
            <div class="cv-item" v-for="item in carouselItems" :key="item.id">
              <div class="cv-item-hd">
                <span class="cv-item-name">{{ keyLabel(item.sectionKey) }}</span>
                <div class="cv-item-ops">
                  <button class="cv-move-btn" title="上移" @click="move(item, -1)">↑</button>
                  <button class="cv-move-btn" title="下移" @click="move(item, 1)">↓</button>
                  <button
                    class="cv-toggle"
                    :class="{ on: item.isActive === 1 }"
                    @click="toggleItem(item)"
                  >
                    {{ item.isActive === 1 ? "已启用" : "已停用" }}
                  </button>
                </div>
              </div>
              <el-form label-width="84px" class="cv-form">
                <el-form-item label="标题">
                  <el-input v-model="item.contentTitle" placeholder="轮播标题" />
                </el-form-item>
                <el-form-item label="图片">
                  <div class="image-upload-row">
                    <img v-if="item.imageUrl" :src="imgUrl(item.imageUrl)" class="cv-thumb" />
                    <span v-else class="cv-thumb-none">无图片</span>
                    <input
                      type="file"
                      accept="image/*"
                      :ref="(el) => (fileInputs[item.id] = el)"
                      style="display: none"
                      @change="(e) => onUpload(e, item)"
                    />
                    <el-button size="small" @click="triggerUpload(item)" :loading="uploadLoading[item.id]">上传</el-button>
                  </div>
                </el-form-item>
                <el-form-item label="副标题">
                  <el-input v-model="item.contentText" type="textarea" :rows="2" placeholder="轮播描述文字" />
                </el-form-item>
                <el-row :gutter="12">
                  <el-col :span="12"><el-form-item label="小标签">
                    <el-input v-model="extraCache[item.id].tag" placeholder="如：热销爆款" />
                  </el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="按钮文字">
                    <el-input v-model="extraCache[item.id].cta" placeholder="如：立即抢购" />
                  </el-form-item></el-col>
                </el-row>
                <el-row :gutter="12">
                  <el-col :span="12"><el-form-item label="图标表情">
                    <el-input v-model="extraCache[item.id].emoji" placeholder="如：🛋️" />
                  </el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="背景色">
                    <el-color-picker v-model="extraCache[item.id].bg" size="default" />
                  </el-form-item></el-col>
                </el-row>
                <el-form-item label="跳转页面">
                  <el-select
                    :model-value="linkValue(item)"
                    class="cv-link-select"
                    @change="(v) => onLinkChange(item, v)"
                  >
                    <el-option
                      v-for="o in linkOptions"
                      :key="o.value"
                      :label="o.label"
                      :value="o.value"
                    />
                  </el-select>
                  <el-input
                    v-if="linkValue(item) === '__custom'"
                    v-model="item.linkUrl"
                    class="cv-link-custom"
                    placeholder="填写完整路径，如 /type/1 或 /ai-chat"
                  />
                </el-form-item>
              </el-form>
            </div>
          </div>
        </template>
      </el-tab-pane>

      <!-- 通用分组：首页文案 / 服务保障 / 关于页保障 / 品牌信息 -->
      <el-tab-pane
        v-for="t in commonTabs"
        :key="t.name"
        :label="t.label"
        :name="t.name"
      >
        <el-empty
          v-if="!filteredBy(t.name).length"
          :description="`暂无${t.label}内容`"
          :image-size="72"
        />
        <div class="cv-list" v-else>
          <div class="cv-item" v-for="item in filteredBy(t.name)" :key="item.id">
            <div class="cv-item-hd">
              <span class="cv-item-name">{{ keyLabel(item.sectionKey) }}</span>
              <div class="cv-item-ops">
                <button v-if="t.movable" class="cv-move-btn" title="上移" @click="move(item, -1)">↑</button>
                <button v-if="t.movable" class="cv-move-btn" title="下移" @click="move(item, 1)">↓</button>
                <button
                  v-if="t.showToggle"
                  class="cv-toggle"
                  :class="{ on: item.isActive === 1 }"
                  @click="toggleItem(item)"
                >
                  {{ item.isActive === 1 ? "已启用" : "已停用" }}
                </button>
              </div>
            </div>
            <el-form label-width="84px" class="cv-form">
              <el-form-item v-if="hasImage(item.sectionKey)" label="图片">
                <div class="image-upload-row">
                  <img v-if="item.imageUrl" :src="imgUrl(item.imageUrl)" class="cv-thumb" />
                  <span v-else class="cv-thumb-none">无图片</span>
                  <input
                    type="file"
                    accept="image/*"
                    :ref="(el) => (fileInputs[item.id] = el)"
                    style="display: none"
                    @change="(e) => onUpload(e, item)"
                  />
                  <el-button size="small" @click="triggerUpload(item)" :loading="uploadLoading[item.id]">上传</el-button>
                </div>
              </el-form-item>
              <el-form-item v-if="hasIcon(item.sectionKey)" label="图标">
                <el-input v-model="extraCache[item.id].icon" placeholder="如：🚚" style="width: 140px" />
              </el-form-item>
              <el-form-item v-if="hasTitle(item.sectionKey)" label="标题">
                <el-input v-model="item.contentTitle" :placeholder="titlePlaceholder(item)" />
              </el-form-item>
              <el-form-item v-if="hasText(item.sectionKey)" label="描述">
                <el-input v-model="item.contentText" type="textarea" :rows="2" placeholder="描述文字" />
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>

      <!-- 联系我们：独立面板 -->
      <el-tab-pane label="联系我们" name="contact">
        <div class="cv-panel">
          <el-form label-width="96px" class="cv-form">
            <el-row :gutter="24">
              <el-col :span="12"><el-form-item label="客服热线">
                <el-input v-model="contactExtra.phone" placeholder="400-000-0000" />
              </el-form-item></el-col>
              <el-col :span="12"><el-form-item label="热线备注">
                <el-input v-model="contactExtra.phoneNote" placeholder="周一至周日 9:00-21:00" />
              </el-form-item></el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="12"><el-form-item label="电子邮箱">
                <el-input v-model="contactExtra.email" placeholder="service@example.com" />
              </el-form-item></el-col>
              <el-col :span="12"><el-form-item label="邮箱备注">
                <el-input v-model="contactExtra.emailNote" placeholder="24 小时内回复" />
              </el-form-item></el-col>
            </el-row>
            <el-form-item label="总部地址">
              <el-input v-model="contactExtra.address" type="textarea" :rows="2" placeholder="总部地址" />
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  getAdminSiteContentList,
  saveSiteContent,
  toggleSiteContent,
  uploadSiteContentImage,
} from "@/api/siteContent.js";
import { imgUrl } from "@/utils/img.js";

const activeGroup = ref("carousel");
const list = ref([]);
const extraCache = reactive({});
const contactExtra = reactive({
  phone: "",
  phoneNote: "",
  email: "",
  emailNote: "",
  address: "",
});
const fileInputs = reactive({});
const uploadLoading = reactive({});
const saving = ref(false);
const previewIndex = ref(0);

const parseExtra = (str) => {
  try {
    return JSON.parse(str) || {};
  } catch {
    return {};
  }
};

/* ---------- 字段显隐规则（与实际前台消费保持一致） ---------- */
const hasImage = (key) => ["hero_1", "hero_2", "hero_3", "system_logo"].includes(key);
const hasIcon = (key) => key.startsWith("value_") || key.startsWith("service_");
const hasTitle = (key) => !["system_logo", "contact_info"].includes(key);
const hasText = (key) =>
  key.startsWith("value_") ||
  key.startsWith("service_") ||
  key.startsWith("hero_") ||
  ["home_categories", "home_products"].includes(key);

const titlePlaceholder = (item) => {
  if (item.sectionKey === "system_name") return "系统/平台名称";
  if (item.sectionKey === "system_tagline") return "品牌标语";
  if (item.sectionKey.startsWith("value_")) return "如：正品保障";
  if (item.sectionKey.startsWith("service_")) return "如：极速发货";
  if (item.sectionKey === "home_categories") return "首页分类区标题";
  return "标题";
};

/* ---------- 跳转页面下拉选项 ---------- */
const linkOptions = [
  { label: "不跳转", value: "" },
  { label: "全部商品", value: "/type/0" },
  { label: "AI 智能导购", value: "/ai-chat" },
  { label: "关于我们", value: "/about" },
  { label: "自定义链接", value: "__custom" },
];

// 当前链接若已在预设选项中则显示对应项，否则显示"自定义链接"
const linkValue = (item) =>
  linkOptions.some((o) => o.value === item.linkUrl) ? item.linkUrl || "" : "__custom";

const onLinkChange = (item, val) => {
  if (val !== "__custom") item.linkUrl = val;
};

/* ---------- 分组定义 ---------- */
const commonTabs = [
  { name: "label", label: "首页文案", showToggle: true, movable: true },
  { name: "service", label: "服务保障", showToggle: true, movable: true },
  { name: "story", label: "关于页保障", showToggle: true, movable: true },
  { name: "brand", label: "品牌信息", showToggle: false, movable: false },
];

const keyLabel = (key) => {
  const map = {
    hero_1: "轮播图 ①",
    hero_2: "轮播图 ②",
    hero_3: "轮播图 ③",
    value_1: "购物保障 ①",
    value_2: "购物保障 ②",
    value_3: "购物保障 ③",
    value_4: "购物保障 ④",
    contact_info: "联系方式",
    service_1: "服务项 ①",
    service_2: "服务项 ②",
    service_3: "服务项 ③",
    service_4: "服务项 ④",
    home_categories: "首页分类区标题",
    home_products: "首页精选好物标题",
    system_name: "系统名称",
    system_tagline: "系统标语",
    system_logo: "系统 Logo",
  };
  return map[key] || key;
};

const filteredBy = (group) =>
  list.value
    .filter((i) => i.sectionGroup === group)
    .sort((a, b) => a.sortOrder - b.sortOrder);

const carouselItems = computed(() => filteredBy("carousel"));
const carouselSlides = computed(() =>
  carouselItems.value.map((s) => ({
    bg: extraCache[s.id]?.bg || "#e8e0d5",
    tag: extraCache[s.id]?.tag || "",
    title: s.contentTitle || "",
    desc: s.contentText || "",
    emoji: extraCache[s.id]?.emoji || "🛋️",
  })),
);

/* ---------- 数据加载 ---------- */
const loadData = async () => {
  try {
    const res = await getAdminSiteContentList();
    if ((res.success || res.code === 200) && Array.isArray(res.data)) {
      list.value = res.data;
      list.value.forEach((item) => {
        extraCache[item.id] = parseExtra(item.extraData);
      });
      const ct = res.data.find((i) => i.sectionKey === "contact_info");
      if (ct) Object.assign(contactExtra, parseExtra(ct.extraData));
    }
  } catch {
    /* ignore */
  }
};

/* ---------- 保存当前分组 ---------- */
const saveGroup = async () => {
  saving.value = true;
  try {
    const items =
      activeGroup.value === "contact"
        ? list.value.filter((i) => i.sectionKey === "contact_info")
        : filteredBy(activeGroup.value);
    for (const it of items) {
      if (it.sectionKey === "contact_info") {
        it.extraData = JSON.stringify({ ...contactExtra });
      } else {
        it.extraData = JSON.stringify(extraCache[it.id] || {});
      }
      const res = await saveSiteContent({ ...it });
      if (!(res.success || res.code === 200)) {
        ElMessage.error(res.msg || `${it.sectionKey || it.id} 保存失败`);
        return;
      }
    }
    ElMessage.success("当前分组已保存，前台已同步");
  } catch {
    ElMessage.error("保存失败，请稍后重试");
  } finally {
    saving.value = false;
  }
};

/* ---------- 排序调整 ---------- */
const move = async (item, dir) => {
  const items = filteredBy(activeGroup.value);
  const i = items.findIndex((x) => x.id === item.id);
  const j = i + dir;
  if (j < 0 || j >= items.length) return;
  [items[i].sortOrder, items[j].sortOrder] = [
    items[j].sortOrder,
    items[i].sortOrder,
  ];
  await saveGroup();
};

/* ---------- 状态切换 ---------- */
const toggleItem = async (item) => {
  try {
    const res = await toggleSiteContent(item.id);
    if (res.success || res.code === 200) {
      item.isActive = res.data;
      ElMessage.success(item.isActive === 1 ? "已启用" : "已停用");
    }
  } catch {
    ElMessage.error("操作失败");
  }
};

/* ---------- 图片上传 ---------- */
const triggerUpload = (item) => {
  fileInputs[item.id]?.click();
};

const onUpload = async (e, item) => {
  const file = e.target.files[0];
  if (!file) return;
  uploadLoading[item.id] = true;
  try {
    const res = await uploadSiteContentImage(file);
    if ((res.success || res.code === 200) && res.data) {
      item.imageUrl = res.data;
      ElMessage.success("上传成功");
    } else {
      ElMessage.error(res.msg || "上传失败");
    }
  } catch {
    ElMessage.error("上传失败");
  } finally {
    uploadLoading[item.id] = false;
  }
  e.target.value = "";
};

onMounted(loadData);
</script>

<style scoped lang="scss">
@import "@/styles/views/site-content-manage.scss";
</style>