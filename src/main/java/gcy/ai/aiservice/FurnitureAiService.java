package gcy.ai.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * 家具商城AI助手服务接口。
 * <p>
 * 基于LangChain4j的AI服务，集成了流式聊天、RAG知识检索和工具调用能力。
 * 使用OpenAI兼容的流式模型，配合Redis聊天记忆和家具查询工具，
 * 为用户提供智能购物咨询助手。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever",
        tools = "furnitureTools"
)
public interface FurnitureAiService {

    /**
     * 用户聊天的方法（流式响应）
     */
    @SystemMessage(fromResource = "system.txt")
    Flux<String> streamChat(@MemoryId String memoryId, @UserMessage String message);
}
