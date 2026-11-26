package gcy.ai.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * 保活探针专用的 AI 服务。
 * <p>
 * 与 {@link FurnitureAiService} 刻意分离：这里不挂会话记忆、不挂 RAG 检索、
 * 不挂任何工具。保活心跳只需要把模型实例和 HTTP 连接池焐热，没必要每 15 分钟
 * 检索一次知识库、并把整段知识库文本写进一条记忆里（旧实现会把
 * {@code keepalive:heartbeat} 撑到几十 KB，纯属白烧 token）。
 * </p>
 *
 * @author 郭名城
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "openAiStreamingChatModel"
)
public interface PingAiService {

    /**
     * 保活心跳方法（流式响应）。
     *
     * @param message 探针消息，固定传 ping
     * @return 仅包含 pong 的流式响应
     */
    @SystemMessage("你是健康检查探针。无论用户输入什么，只回复pong一个词，禁止输出任何其他内容。")
    Flux<String> ping(@UserMessage String message);
}