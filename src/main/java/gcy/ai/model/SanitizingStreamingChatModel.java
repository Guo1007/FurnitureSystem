package gcy.ai.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import gcy.ai.util.ToolArgumentSanitizer;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 会清洗工具调用参数的流式聊天模型装饰器。
 * <p>
 * LangChain4j 在 {@code onCompleteResponse} 里会先把含 tool call 的 AiMessage 写入
 * 会话记忆，再执行工具；而工具执行前会用 Jackson 解析 {@code arguments} 字符串。
 * 模型一旦输出 {@code None}、空值等非标 JSON，解析就会抛异常，整条流式响应失败，
 * 坏 AiMessage 还会留在记忆里。
 * </p>
 * <p>
 * 装饰器在模型返回的 {@link ChatResponse} 进入 AiService 之前，把每个
 * {@link ToolExecutionRequest} 的参数清洗为合法 JSON。这样记忆和工具执行拿到的
 * 都是干净参数，从源头避免上述异常。
 * </p>
 *
 * @author 郭名城
 */
@RequiredArgsConstructor
public class SanitizingStreamingChatModel implements StreamingChatModel {

    /**
     * 被装饰的真实模型。
     */
    private final StreamingChatModel delegate;

    /**
     * 工具参数清洗器。
     */
    private final ToolArgumentSanitizer sanitizer;

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        delegate.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                handler.onPartialThinking(partialThinking);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                handler.onPartialToolCall(partialToolCall);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                handler.onCompleteToolCall(completeToolCall);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                handler.onCompleteResponse(sanitize(response));
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        });
    }

    /**
     * 清洗响应中的所有工具调用参数；没有工具调用时原样返回。
     */
    private ChatResponse sanitize(ChatResponse response) {
        AiMessage aiMessage = response.aiMessage();
        if (aiMessage == null || !aiMessage.hasToolExecutionRequests()) {
            return response;
        }
        List<ToolExecutionRequest> sanitized = aiMessage.toolExecutionRequests().stream()
                .map(request -> {
                    String cleaned = sanitizer.sanitize(request.name(), request.arguments());
                    if (cleaned.equals(request.arguments())) {
                        return request;
                    }
                    return ToolExecutionRequest.builder()
                            .id(request.id())
                            .name(request.name())
                            .arguments(cleaned)
                            .build();
                })
                .toList();
        AiMessage cleanedMessage = AiMessage.builder()
                .text(aiMessage.text())
                .thinking(aiMessage.thinking())
                .toolExecutionRequests(sanitized)
                .attributes(aiMessage.attributes())
                .build();
        return response.toBuilder().aiMessage(cleanedMessage).build();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}