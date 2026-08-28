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
      <!-- 左侧聊天区 -->
      <section class="chat-main">
        <div class="chat-header">
          <div class="header-left">
            <div class="bot-avatar">智</div>
            <div class="header-text">
              <h2>小智 AI 助手</h2>
              <p>随时为您解答家具选购疑问</p>
            </div>
          </div>
          <div class="header-actions">
            <button class="icon-btn" @click="newChat" title="新对话">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
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
            <input
              v-model="inputMessage"
              placeholder="输入您的问题..."
              :disabled="loading"
              @keydown.enter="sendMessage()"
              ref="inputRef"
            />
            <button
              class="send-btn"
              :disabled="!inputMessage.trim() || loading"
              @click="sendMessage()"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
            </button>
          </div>
        </div>
      </section>

      <!-- 右侧侧边栏 -->
      <aside class="chat-sidebar">
        <div class="sidebar-card">
          <h4>🕐 在线时间</h4>
          <p>7×24 小时在线，随时为您服务</p>
        </div>
        <div class="sidebar-card">
          <h4>📦 常见问题</h4>
          <ul>
            <li>如何选择合适的家具尺寸？</li>
            <li>配送时效及运费说明</li>
            <li>退换货政策及流程</li>
            <li>实木家具日常保养</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
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

// 聊天记录按登录用户隔离存储，避免退出登录后其他账号看到历史记录
const userStore = useUserStore();
const chatScope = () =>
  userStore.userInfo?.id ? `u${userStore.userInfo.id}` : "guest";
const chatStorageKey = () => `aiChatMessages:${chatScope()}`;
const productStorageKey = () => `aiProductCache:${chatScope()}`;
const conversationStorageKey = () => `aiConversationId:${chatScope()}`;

const messages = ref(loadMessages());
const conversationId = ref(
  localStorage.getItem(conversationStorageKey()) || "",
);

function loadMessages() {
  try {
    const saved = localStorage.getItem(chatStorageKey());
    if (!saved) return [];
    const all = JSON.parse(saved);
    const cutoff = Date.now() - CHAT_MAX_AGE;
    // 仅保留 3 天内的聊天记录；无有效时间字段的历史消息视为过期一并清除
    return all.filter(m => m.time && m.time > cutoff);
  } catch { return []; }
}

function saveMessages() {
  try {
    // 保存时同样只保留 3 天内的消息，避免旧数据长期累积在 localStorage
    const cutoff = Date.now() - CHAT_MAX_AGE;
    const recent = messages.value.filter(m => m.time && m.time > cutoff);
    localStorage.setItem(chatStorageKey(), JSON.stringify(recent));
  } catch { /* ignore quota */ }
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

let abortController = null;

onMounted(() => {
  inputRef.value?.focus();
  // 恢复历史消息中的商品卡片
  if (messages.value.length > 0) {
    const allIds = new Set();
    messages.value.forEach(m => {
      extractProductIds(m.content).forEach(id => allIds.add(id));
    });
    if (allIds.size > 0) loadProductInfo([...allIds]);
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
  messages.value = [];
  productCache.value = {};
  cacheVersion.value = 0;
  conversationId.value = "";
  localStorage.removeItem(conversationStorageKey());
  localStorage.removeItem(chatStorageKey());
  localStorage.removeItem(productStorageKey());
};

// 消息变化时自动持久化
watch(messages, saveMessages, { deep: true });

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

const sendMessage = async (text) => {
  const msg = text || inputMessage.value.trim();
  if (!msg || loading.value) return;

  messages.value.push({ role: "user", content: msg, time: Date.now() });
  inputMessage.value = "";
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
        conversationId: conversationId.value || null,
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
            conversationId.value = parsed.conversationId;
            localStorage.setItem(conversationStorageKey(), parsed.conversationId);
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
