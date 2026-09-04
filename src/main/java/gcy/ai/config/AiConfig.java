package gcy.ai.config;

import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static gcy.system.utils.RedisConstants.EMBEDDING_INGESTED_KEY;
import static gcy.system.utils.RedisConstants.EMBEDDING_WILDCARD_KEY;

/**
 * AI聊天功能配置类。
 * <p>
 * 配置 LangChain4j 的聊天记忆、向量存储、RAG内容检索等组件。
 * 使用 Redis 作为聊天记忆和向量嵌入的持久化存储。
 * </p>
 * <p>
 * RAG 知识库与 Tool Calling 分工策略：
 * - Tool Calling 负责产品数据（商品、库存、价格）查询，始终从数据库实时获取最新数据
 * - RAG 知识库负责静态政策/FAQ（如退换货规则、配送说明、系统功能简介），启动时从 classpath:content 目录加载
 * - System Prompt 中已明确指示 AI 优先使用 Tool 查询产品数据，RAG 仅作补充参考
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiConfig {

    /**
     * 向量Embedding数据有效期，30天
     */
    private static final Duration EMBEDDING_TTL = Duration.ofDays(30);

    /**
     * Redis聊天记忆存储
     */
    private final ChatMemoryStore redisChatMemoryStore;

    /**
     * Redis向量嵌入存储
     */
    private final RedisEmbeddingStore redisEmbeddingStore;

    /**
     * Redis字符串操作模板
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建聊天记忆提供者。
     * <p>
     * 使用Redis存储聊天记忆，每个会话保留最近20条消息的上下文窗口。
     * </p>
     *
     * @return 聊天记忆提供者实例
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
    }

    /**
     * 创建向量嵌入存储实例。
     * <p>
     * 首次启动时自动加载 classpath:content 目录下的知识库文档，
     * 将其分割、向量化后存入Redis，后续重启直接复用已有向量数据。
     * </p>
     *
     * @return 向量嵌入存储实例
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        Boolean alreadyIngested = stringRedisTemplate.hasKey(EMBEDDING_INGESTED_KEY);
        if (alreadyIngested) {
            log.info("知识库向量已存在，跳过 Embedding 摄入");
            return redisEmbeddingStore;
        }
        log.info("开始摄入知识库文档到向量存储...");
        List<Document> documents = ClassPathDocumentLoader.loadDocuments("content");
        // maxSegmentSizeInChars：每一段文档切片，最多允许多少个字符。
        // maxOverlapSizeInChars：相邻两块之间，重复保留多少字符。
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(redisEmbeddingStore)
                .documentSplitter(splitter)
                .build();
        ingestor.ingest(documents);
        // 用 SCAN 遍历而非 KEYS，避免 O(N) 阻塞单线程 Redis
        Set<String> embeddingKeys = new HashSet<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(EMBEDDING_WILDCARD_KEY).count(100).build())) {
            while (cursor.hasNext()) {
                embeddingKeys.add(cursor.next());
            }
        }
        if (!embeddingKeys.isEmpty()) {
            for (String key : embeddingKeys) {
                stringRedisTemplate.expire(key, EMBEDDING_TTL);
            }
            log.info("已为 {} 个向量 key 设置过期时间 {} 天", embeddingKeys.size(), EMBEDDING_TTL.toDays());
        }
        stringRedisTemplate.opsForValue()
                .set(EMBEDDING_INGESTED_KEY, String.valueOf(System.currentTimeMillis()), EMBEDDING_TTL);
        log.info("知识库文档摄入完成，向量数据有效期 {} 天", EMBEDDING_TTL.toDays());
        return redisEmbeddingStore;
    }

    /**
     * 创建RAG内容检索器。
     * <p>
     * 基于向量相似度从知识库中检索相关文档片段，最低匹配分数0.5，最多返回3条结果。
     * </p>
     *
     * @param embeddingStore 向量嵌入存储
     * @return 内容检索器实例
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .minScore(0.5)
                .maxResults(3)
                .build();
    }
}
