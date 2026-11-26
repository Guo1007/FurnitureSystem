package gcy.ai.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import gcy.ai.util.ToolArgumentSanitizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link SanitizingStreamingChatModel} 单元测试。
 */
class SanitizingStreamingChatModelTest {

    @Test
    void sanitizesToolArgumentsBeforeResponseReachesAiService() {
        ToolExecutionRequest badRequest = ToolExecutionRequest.builder()
                .id("call_1")
                .name("planFurnitureSet")
                .arguments("{\"scene\": \"卧室\", \"budget\": None}")
                .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("", List.of(badRequest)))
                .build();

        StreamingChatModel delegate = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onCompleteResponse(response);
            }
        };

        SanitizingStreamingChatModel model =
                new SanitizingStreamingChatModel(delegate, new ToolArgumentSanitizer());
        AtomicReference<ChatResponse> captured = new AtomicReference<>();

        model.chat(ChatRequest.builder().messages(UserMessage.from("给我配个小次卧")).build(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completed) {
                        captured.set(completed);
                    }

                    @Override
                    public void onError(Throwable error) {
                        fail(error);
                    }
                });

        ToolExecutionRequest fixed = captured.get().aiMessage()
                .toolExecutionRequests().get(0);
        assertEquals("{\"scene\": \"卧室\", \"budget\": null}", fixed.arguments());
    }
}