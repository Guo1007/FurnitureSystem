<template>
  <div class="ai-chat-page">
    <!-- Breadcrumb -->
    <div class="page-breadcrumb">
      <button class="breadcrumb-back" @click="goBack" title="返回">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
      </button>
      <router-link to="/">首页</router-link>
      <span>/</span>
      <span class="current">智能客服</span>
    </div>

    <div class="chat-container">
      <!-- 左侧：会话历史栏 -->
      <aside class="sessions-panel" :class="{ open: drawerOpen }">
        <div class="sessions-head">
          <span class="sessions-title">会话历史</span>
          <button class="icon-btn" @click="newChat" title="新建对话" :disabled="loading">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
          </button>
        </div>

        <div class="session-list">
          <div
            v-for="s in conversations"
            :key="s.id"
            class="session-item"
            :class="{ 'is-active': s.id === activeId }"
            @click="openSession(s.id)"
          >
            <div class="session-item-main">
              <div class="session-item-title">{{ s.title || '新对话' }}</div>
              <div class="session-item-meta">
                <span>{{ s.messages.length }} 条</span>
                <span>{{ fmtTime(s.updatedAt) }}</span>
              </div>
            </div>
            <div class="session-item-ops" @click.stop>
              <button class="op-btn" title="重命名" @click="renameSession(s)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg>
              </button>
              <button class="op-btn danger" title="删除" @click="deleteSession(s)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/></svg>
              </button>
            </div>
          </div>

          <div v-if="conversations.length === 0" class="session-empty">
            <span class="session-empty-icon">🗂️</span>
            <p>暂无历史会话</p>
            <p class="session-empty-sub">点击上方 + 开启新对话</p>
          </div>
        </div>

        <div class="sessions-foot" v-if="conversations.length > 0">
          <span>仅保留近 {{ CHAT_MAX_DAYS }} 天的会话</span>
        </div>
      </aside>

      <!-- 移动端抽屉遮罩 -->
      <div class="drawer-mask" v-if="drawerOpen" @click="drawerOpen = false"></div>

      <!-- 右侧：对话区 -->
      <section class="chat-main">
        <div class="chat-header">
          <div class="header-left">
            <button class="icon-btn drawer-toggle" @click="drawerOpen = true" title="会话列表">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M3 6h18M3 12h18M3 18h18"/></svg>
            </button>
            <div class="bot-avatar">智</div>
            <div class="header-text">
              <h2>{{ activeTitle || '小智 AI 助手' }}</h2>
              <p>随时为您解答家具选购疑问</p>
            </div>
          </div>
          <div class="header-actions">
            <button class="icon-btn" @click="newChat" title="新对话" :disabled="loading">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
            </button>
          </div>
        </div>

        <!-- 消息区 -->
        <div class="chat-body" ref="bodyRef">
          <div class="retention-hint" v-if="messages.length > 0">
            <span>💬 仅保留近 {{ CHAT_MAX_DAYS }} 天的聊天记录</span>
          </div>

          <div v-if="messages.length === 0" class="welcome-block">
            <div class="welcome-emoji">🛋️</div>
            <h3>你好，我是小智</h3>
            <p>家具商城的智能客服助手，试试问我：</p>
            <div class="quick-chips">
              <button
                v-for="q in quickQuestions"
                :key="q"
                class="chip"
                @click="sendMessage(q)"
              >{{ q }}</button>
            </div>
          </div>

          <div
            v-for="(msg, i) in messages"
            :key="i"
            class="msg-row"
            :class="msg.role"
          >
            <div class="msg-avatar">
              <span v-if="msg.role === 'assistant'" class="avatar-text">小智</span>
              <span v-else>👤</span>
            </div>
            <div class="msg-body">
              <div class="msg-bubble" v-html="fmt(msg.content, cacheVersion)"></div>
              <span class="msg-time" v-if="msg.time">{{ fmtTime(msg.time) }}</span>
              <span v-if="msg.role === 'assistant' && i === messages.length - 1 && loading" class="typing-dots">
                <i></i><i></i><i></i>
              </span>
            </div>
          </div>

          <!-- 等待首条AI回复时的加载动画 -->
          <div v-if="loading && messages.length > 0 && messages[messages.length - 1].role === 'user'" class="msg-row assistant">
            <div class="msg-avatar"><span class="avatar-text">小智</span></div>
            <div class="msg-body">
              <div class="msg-bubble thinking">
                <div class="typing-dots"><i></i><i></i><i></i></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-footer">
          <div class="input-row">
            <textarea
              v-model="inputMessage"
              ref="inputRef"
              rows="1"
              placeholder="输入您的问题，Enter 发送，Shift + Enter 换行"
              @keydown.enter="onEnter"
              @input="autoResize"
            ></textarea>
            <button
              v-if="!loading"
              class="send-btn"
              :disabled="!inputMessage.trim()"
              @click="sendMessage()"
              title="发送"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
            </button>
            <button v-else class="send-btn stop" @click="stopGenerate" title="停止生成">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { imgUrl } from "@/utils/img.js";
import { useBackNavigation } from "@/composables/useBackNavigation.js";
import { useUserStore } from "@/stores/user";

const { goBack } = useBackNavigation();

const CHAT_MAX_DAYS = 3;
const CHAT_MAX_AGE = CHAT_MAX_DAYS * 24 * 60 * 60 * 1000;

const inputMessage = ref("");
const loading = ref(false);
const bodyRef = ref(null);
const inputRef = ref(null);
const drawerOpen = ref(false);

// 会话数据按登录用户隔离存储，避免退出登录后其他账号看到历史记录
const userStore = useUserStore();
const chatScope = () =>
  userStore.userInfo?.id ? `u${userStore.userInfo.id}` : "guest";
// 新版：多会话列表结构 [{id, title, createdAt, updatedAt, messages}]
const convsStorageKey = () => `aiConversations:${chatScope()}`;
// 旧版单会话存储键（用于数据迁移）
const chatStorageKey = () => `aiChatMessages:${chatScope()}`;
const conversationStorageKey = () => `aiConversationId:${chatScope()}`;
// 商品卡片缓存 — 按用户持久化，跨会话共享
const productStorageKey = () => `aiProductCache:${chatScope()}`;

// 仅保留 3 天内的消息；无有效时间字段的历史消息视为过期一并清除
const filterRecent = (list) =>
  (list || []).filter(m => m.time && m.time > Date.now() - CHAT_MAX_AGE);

/**
 * 加载多会话列表，兼容旧版单会话数据迁移。
 * 旧数据（aiChatMessages + aiConversationId）在首次加载时转换为一条会话记录，
 * 迁移后清空旧键。迁移会话若无后端 conversationId，使用 legacy- 前缀临时 ID，
 * 首次续聊时由后端 meta 事件返回真实 ID 后替换。
 */
function loadConversations() {
  try {
    const saved = localStorage.getItem(convsStorageKey());
    if (saved) {
      const list = JSON.parse(saved);
      if (!Array.isArray(list)) return [];
      const cutoff = Date.now() - CHAT_MAX_AGE;
      return list
        .filter(c => c && c.updatedAt && c.updatedAt > cutoff)
        .map(c => ({ ...c, messages: filterRecent(c.messages) }))
        .sort((a, b) => b.updatedAt - a.updatedAt);
    }
    // 旧版单会话数据迁移
    const oldRaw = localStorage.getItem(chatStorageKey());
    if (!oldRaw) return [];
    const oldId = localStorage.getItem(conversationStorageKey()) || "";
    localStorage.removeItem(chatStorageKey());
    localStorage.removeItem(conversationStorageKey());
    let all = [];
    try { all = JSON.parse(oldRaw); } catch { return []; }
    const recent = filterRecent(all);
    if (recent.length === 0) return [];
    const firstUser = recent.find(m => m.role === "user");
    return [{
      id: oldId || `legacy-${Date.now()}`,
      title: (firstUser ? firstUser.content : "历史会话").slice(0, 20),
      createdAt: recent[0].time,
      updatedAt: recent[recent.length - 1].time,
      messages: recent,
    }];
  } catch { return []; }
}

function saveConversations() {
  try {
    localStorage.setItem(convsStorageKey(), JSON.stringify(conversations.value));
  } catch { /* ignore quota */ }
}

const conversations = ref(loadConversations());
const activeId = ref("");

const activeConv = () => conversations.value.find(c => c.id === activeId.value);
const activeTitle = computed(() => activeConv()?.title || "");

const messages = ref([]);

let skipPersist = false;

// 消息变化时同步到当前会话并持久化
watch(messages, () => {
  if (skipPersist) { skipPersist = false; return; }
  persistToActive();
}, { deep: true });

function persistToActive() {
  const conv = activeConv();
  if (!conv) return;
  conv.messages = filterRecent(messages.value);
  conv.updatedAt = Date.now();
  saveConversations();
}

// 商品卡片缓存 — 按用户持久化到 localStorage
const productCache = ref(loadProductCache());
const cacheVersion = ref(0);

function loadProductCache() {
  try {
    const saved = localStorage.getItem(productStorageKey());
    return saved ? JSON.parse(saved) : {};
  } catch { return {}; }
}

function saveProductCache() {
  try {
    localStorage.setItem(productStorageKey(), JSON.stringify(productCache.value));
  } catch { /* ignore */ }
}

const extractProductIds = (content) => {
  const ids = [];
  const regex = /\[商品:(\d+)\]/g;
  let match;
  while ((match = regex.exec(content)) !== null) {
    ids.push(parseInt(match[1]));
  }
  return [...new Set(ids)];
};

const loadProductInfo = async (ids) => {
  const missingIds = ids.filter((id) => !productCache.value[id]);
  if (missingIds.length === 0) return;
  const token = localStorage.getItem("token") || "";
  for (const id of missingIds) {
    try {
      const res = await fetch(`/api/furniture/${id}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const json = await res.json();
        if (json.data) {
          productCache.value[id] = json.data;
        }
      }
    } catch (err) { /* ignore */ }
  }
  saveProductCache();
  cacheVersion.value++;
};

/** 恢复当前消息中引用的商品卡片 */
const restoreProductCards = () => {
  if (messages.value.length === 0) return;
  const allIds = new Set();
  messages.value.forEach(m => {
    extractProductIds(m.content).forEach(id => allIds.add(id));
  });
  if (allIds.size > 0) loadProductInfo([...allIds]);
};

let abortController = null;

onMounted(async () => {
  inputRef.value?.focus();
  // 默认打开最近一个会话
  if (conversations.value.length > 0) {
    openSession(conversations.value[0].id);
  } else {
    restoreProductCards();
  }
});

onBeforeUnmount(() => {
  if (abortController) abortController.abort();
});

const quickQuestions = ref([
  "有什么推荐的沙发？",
  "家具配送要多久？",
  "如何申请退换货？",
  "实木家具怎么保养？",
]);

const newChat = () => {
  if (loading.value) {
    ElMessage.warning("正在生成回复，请稍候");
    return;
  }
  activeId.value = "";
  skipPersist = true;
  messages.value = [];
  drawerOpen.value = false;
  nextTick(() => inputRef.value?.focus());
};

const openSession = (id) => {
  if (loading.value) {
    ElMessage.warning("正在生成回复，请稍候再切换");
    return;
  }
  if (id === activeId.value) {
    drawerOpen.value = false;
    return;
  }
  const conv = conversations.value.find(c => c.id === id);
  if (!conv) return;
  activeId.value = id;
  skipPersist = true;
  messages.value = conv.messages.map(m => ({ ...m }));
  drawerOpen.value = false;
  restoreProductCards();
  scrollToBottom();
};

const renameSession = async (s) => {
  try {
    const { value } = await ElMessageBox.prompt("请输入会话标题", "重命名会话", {
      inputValue: s.title || "",
      inputPlaceholder: "会话标题",
      confirmButtonText: "保存",
      cancelButtonText: "取消",
    });
    if (value && value.trim()) {
      s.title = value.trim().slice(0, 30);
      saveConversations();
      ElMessage.success("已重命名");
    }
  } catch { /* 取消 */ }
};

const deleteSession = async (s) => {
  try {
    await ElMessageBox.confirm("删除后该会话的聊天记录将无法恢复，确认删除？", "删除会话", {
      type: "warning",
      confirmButtonText: "确认删除",
      cancelButtonText: "取消",
    });
  } catch { return; }
  conversations.value = conversations.value.filter(c => c.id !== s.id);
  saveConversations();
  if (s.id === activeId.value) {
    activeId.value = "";
    skipPersist = true;
    messages.value = [];
  }
  ElMessage.success("已删除");
};

const scrollToBottom = () => {
  nextTick(() => {
    if (bodyRef.value) {
      bodyRef.value.scrollTop = bodyRef.value.scrollHeight;
    }
  });
};

const fmtTime = (ts) => {
  const d = new Date(ts);
  const now = new Date();
  const pad = n => String(n).padStart(2, '0');
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (d.toDateString() === now.toDateString()) return `今天 ${hm}`;
  const yesterday = new Date(now); yesterday.setDate(yesterday.getDate() - 1);
  if (d.toDateString() === yesterday.toDateString()) return `昨天 ${hm}`;
  return `${d.getMonth() + 1}/${d.getDate()} ${hm}`;
};

const escapeHtml = (str) => {
  if (!str) return "";
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
};

const fmt = (content, _ver) => {
  if (!content) return "";
  const escaped = content
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
  let formatted = escaped
    .replace(/\n/g, "<br>")
    .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
    .replace(/\*(.*?)\*/g, "<em>$1</em>");
  // 替换 [商品:ID] 为可点击的商品卡片
  formatted = formatted.replace(
    /\[商品:(\d+)\]/g,
    (match, id) => {
      const product = productCache.value[id];
      const imgSrc = product?.fIcon ? imgUrl(product.fIcon) : null;
      if (product) {
        return `<a href="/furniture/detail/${id}" class="product-chip">
          <span class="pchip-thumb">
            ${imgSrc ? `<img src="${escapeHtml(imgSrc)}" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'"/>` : ''}
            <span class="pchip-emoji" style="display:${imgSrc?'none':'flex'}">🪑</span>
          </span>
          <span class="pchip-name">${escapeHtml(product.fName || '')}</span>
          <span class="pchip-divider"></span>
          <span class="pchip-price">¥${escapeHtml(String(product.price || ''))}</span>
        </a>`;
      }
      return `<a href="/furniture/detail/${id}" class="product-chip">
        <span class="pchip-thumb"><span class="pchip-emoji">🪑</span></span>
        <span class="pchip-name">商品 #${id}</span>
      </a>`;
    }
  );
  return formatted;
};

/** 输入框自动增高（最多约 5 行） */
const autoResize = () => {
  const el = inputRef.value;
  if (!el) return;
  el.style.height = "auto";
  el.style.height = Math.min(el.scrollHeight, 130) + "px";
};

const onEnter = (e) => {
  if (e.shiftKey) return;
  e.preventDefault();
  sendMessage();
};

const stopGenerate = () => {
  if (abortController) abortController.abort();
};

const sendMessage = async (text) => {
  const msg = text || inputMessage.value.trim();
  if (!msg || loading.value) return;

  messages.value.push({ role: "user", content: msg, time: Date.now() });
  inputMessage.value = "";
  nextTick(autoResize);
  loading.value = true;
  scrollToBottom();

  try {
    if (abortController) abortController.abort();
    abortController = new AbortController();

    const response = await fetch("/api/ai/chat/stream", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
      body: JSON.stringify({
        message: msg,
        // legacy- 前缀为迁移的旧会话临时 ID，不传给后端，由后端重新生成
        conversationId: activeId.value && !activeId.value.startsWith("legacy-")
          ? activeId.value : null,
      }),
      signal: abortController.signal,
    });

    if (!response.ok) throw new Error("请求失败");

    messages.value.push({ role: "assistant", content: "", time: Date.now() });
    const lastIdx = messages.value.length - 1;

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        // 兼容 "data: " 和 "data:" 两种格式
        let data = "";
        if (line.startsWith("data: ")) {
          data = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
          data = line.slice(5).trim();
        }
        if (!data || data === "[DONE]") continue;

        try {
          const parsed = JSON.parse(data);
          if (parsed.type === "meta" && parsed.conversationId) {
            const newId = parsed.conversationId;
            if (!activeId.value) {
              // 首条消息：创建会话记录入列表
              activeId.value = newId;
              conversations.value.unshift({
                id: newId,
                title: msg.slice(0, 20),
                createdAt: Date.now(),
                updatedAt: Date.now(),
                messages: [],
              });
              persistToActive();
            } else if (activeId.value.startsWith("legacy-")) {
              // 迁移旧会话首次续聊：替换为后端真实 ID
              const conv = activeConv();
              if (conv) conv.id = newId;
              activeId.value = newId;
              saveConversations();
            }
            continue;
          }
          if (parsed.content) {
            messages.value[lastIdx].content += parsed.content;
            scrollToBottom();
          }
          if (parsed.error) {
            messages.value[lastIdx].content = parsed.error;
          }
        } catch (e) {
          // 忽略解析失败的行
        }
      }
    }

    // 加载消息中引用的商品信息
    const allContent = messages.value
      .filter((m) => m.role === "assistant")
      .map((m) => m.content)
      .join(" ");
    const productIds = extractProductIds(allContent);
    if (productIds.length > 0) {
      await loadProductInfo(productIds);
      messages.value = [...messages.value];
    }
  } catch (e) {
    if (e.name !== "AbortError") {
      messages.value.push({
        role: "assistant",
        content: "抱歉，服务暂时不可用，请稍后再试。",
        time: Date.now(),
      });
    }
  } finally {
    loading.value = false;
    // 停止/中断时若 AI 无任何回复内容，移除空消息
    const last = messages.value[messages.value.length - 1];
    if (last && last.role === "assistant" && !last.content) {
      messages.value.pop();
    }
    scrollToBottom();
  }
};
</script>

<style scoped lang="scss">
@import "@/styles/views/ai-chat-page.scss";
</style>

<!-- v-html 卡片样式，不能 scoped -->
<style lang="scss">
@import "@/styles/views/ai-chat-page.scss";
</style>
