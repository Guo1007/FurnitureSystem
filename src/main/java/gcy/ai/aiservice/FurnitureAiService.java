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

    /**
     * 保活心跳方法（流式响应）。
     * <p>
     * 使用专用探针提示词，仅返回 pong 单词，用于定时保活任务预热
     * 模型实例和本地HTTP连接池，消除空闲冷启动问题。
     * </p>
     */
    @SystemMessage("你是健康检查探针。无论用户输入什么，只回复pong一个词，禁止输出任何其他内容。")
    Flux<String> ping(@MemoryId String memoryId, @UserMessage String message);
}
