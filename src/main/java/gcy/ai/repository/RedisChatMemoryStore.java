package gcy.ai.repository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * 基于Redis的聊天记忆存储实现。
 * <p>
 * 将LangChain4j的聊天消息序列化后存入Redis，支持消息的存取和删除操作。
 * 聊天记忆数据默认保存7天，过期后自动清除。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    /**
     * 聊天记忆数据有效期，7天。
     * <p>
     * 必须 ≥ 前端会话保留期（{@code AiChatPage.vue} 的 {@code CHAT_MAX_DAYS}，当前 3 天）：
     * 只要用户还能在前端看到某个会话，模型就应当有对应上下文，反过来才是 bug。
     * 留几天余量即可，不必与前端完全相等。
     */
    private static final Duration MEMORY_TTL = Duration.ofDays(7);

    /**
     * Redis字符串操作模板
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 根据会话ID获取聊天历史消息。
     *
     * @param memoryId 会话标识ID
     * @return 该会话的历史聊天消息列表，未找到则返回空列表
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = memoryId.toString();
        String jsonMessage = redisTemplate.opsForValue().get(key);
        try {
            return ChatMessageDeserializer.messagesFromJson(jsonMessage);
        } catch (RuntimeException e) {
            // 整段记忆是一次性反序列化的，任何一条真正损坏的消息都会让整个会话
            // 每次请求都失败。与其永久卡死，不如丢弃这份记忆，让用户从新上下文继续。
            log.warn("会话记忆反序列化失败，已丢弃该会话的损坏记忆: memoryId={}", memoryId, e);
            redisTemplate.delete(key);
            return List.of();
        }
    }

    /**
     * 更新指定会话的聊天消息，并重置有效期。
     *
     * @param memoryId 会话标识ID
     * @param list     最新的聊天消息列表
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        String key = memoryId.toString();
        String json = ChatMessageSerializer.messagesToJson(list);
        redisTemplate.opsForValue().set(key, json, MEMORY_TTL);
    }

    /**
     * 删除指定会话的所有聊天消息。
     *
     * @param memoryId 会话标识ID
     */
    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(memoryId.toString());
    }
}
