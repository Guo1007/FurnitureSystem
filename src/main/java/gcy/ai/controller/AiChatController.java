package gcy.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gcy.ai.aiservice.FurnitureAiService;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.Favorite;
import gcy.system.entity.pojo.Furniture;
import gcy.system.mapper.FavoriteMapper;
import gcy.system.mapper.FurnitureMapper;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static gcy.system.utils.RedisConstants.AI_CHAT_MEMORY_KEY;

/**
 * AI聊天控制器，处理用户与AI助手的对话交互。
 * <p>
 * 提供基于SSE（Server-Sent Events）的流式聊天接口，支持多轮对话上下文管理。
 * 每次对话通过 conversationId 标识，首次请求自动生成会话ID并通过元事件返回给客户端。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "AI聊天", description = "AI聊天相关接口")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiChatController {

    private final FurnitureAiService furnitureAiService;

    private final FavoriteMapper favoriteMapper;

    private final FurnitureMapper furnitureMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * AI流式聊天接口，以SSE格式实时推送AI回复内容。
     * <p>
     * 客户端发送用户消息后，服务端通过SSE流逐步返回AI生成的回复文本。
     * 若请求中未携带 conversationId，则自动创建新会话并通过 meta 事件返回会话ID；
     * 若携带已有 conversationId，则基于历史对话上下文继续交互。
     * </p>
     *
     * @param request 聊天请求体，包含用户消息内容和可选的会话ID
     * @return SSE格式的响应流，包含meta事件（新会话时）、content事件（AI回复片段）、
     * [DONE]结束标记以及异常时的error事件
     */
    @Operation(summary = "AI流式聊天")
    @PostMapping(value = "/chat/stream", produces = "text/event-stream;charset=utf-8")
    public Flux<String> chatStream(@Parameter(description = "请求体") @Valid @RequestBody ChatRequest request) {
        String message = request.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return Flux.just(errorJson("消息内容不能为空"));
        }
        UserDTO user = UserHolder.getUser();
        Long userId = (user != null) ? user.getId() : 0L;
        String conversationId = request.getConversationId();
        boolean isNew = false;
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = UUID.randomUUID().toString().replace("-", "");
            isNew = true;
        }
        String memoryId = AI_CHAT_MEMORY_KEY + userId + ":" + conversationId;
        log.debug("AI chat: userId={}, conversationId={}, isNew={}, message={}",
                userId, conversationId, isNew, message);
        Flux<String> metaEvent = isNew
                ? Flux.just(metaJson(conversationId))
                : Flux.empty();
        String userContext = buildUserContext(userId);
        String enhancedMessage = userContext.isEmpty() ? message : userContext + "\n\n用户消息：" + message;
        Flux<String> chatStream = furnitureAiService.streamChat(memoryId, enhancedMessage)
                .map(this::contentJson)
                .concatWith(Flux.just("[DONE]"))
                .onErrorResume(e -> {
                    // 首次失败多为冷启动（模型唤醒/连接重建），第一次请求已触发唤醒，
                    // 立即自动重试一次大概率成功，用户无感知，无需用户手动"再问一次"
                    log.error("AI聊天流式调用失败，触发自动重试（冷启动唤醒）: {}", e.getMessage(), e);
                    return furnitureAiService.streamChat(memoryId, enhancedMessage)
                            .map(this::contentJson)
                            .concatWith(Flux.just("[DONE]"))
                            .onErrorResume(e2 -> {
                                log.error("AI自动重试仍失败: {}", e2.getMessage(), e2);
                                return Flux.just(errorJson("AI客服暂时无法响应，请稍后再试"));
                            });
                });

        return Flux.concat(metaEvent, chatStream);
    }

    /**
     * 构建元信息JSON，用于在新建会话时通知客户端会话ID。
     *
     * @param conversationId 新生成的会话唯一标识
     * @return 包含 type 为 "meta" 和 conversationId 的JSON字符串
     */
    private String metaJson(String conversationId) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "meta");
            node.put("conversationId", conversationId);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"meta\"}";
        }
    }

    /**
     * 构建AI回复内容的JSON，用于封装单个流式文本片段。
     *
     * @param content AI生成的文本片段内容
     * @return 包含 content 字段的JSON字符串
     */
    private String contentJson(String content) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("content", content);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"content\":\"\"}";
        }
    }

    /**
     * 构建错误信息JSON，用于向前端返回异常提示。
     *
     * @param errorMsg 错误描述信息
     * @return 包含 error 字段的JSON字符串
     */
    private String errorJson(String errorMsg) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("error", errorMsg);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"序列化异常\"}";
        }
    }

    /**
     * 构建用户上下文信息，用于注入到 AI 消息中实现个性化推荐。
     * <p>
     * 收集当前登录用户的收藏商品列表，拼接为上下文文本。
     * 若用户未登录，则返回空字符串。
     * </p>
     *
     * @param userId 当前登录用户ID
     * @return 用户上下文信息文本，未登录时返回空字符串
     */
    private String buildUserContext(Long userId) {
        if (userId == null || userId == 0) {
            return "";
        }
        StringBuilder ctx = new StringBuilder();
        List<Favorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId));
        if (!favorites.isEmpty()) {
            // 批量查询所有收藏商品，避免 N+1
            List<Long> furnitureIds = favorites.stream()
                    .limit(5)
                    .map(Favorite::getFurnitureId)
                    .collect(Collectors.toList());
            Map<Long, Furniture> furnitureMap = furnitureMapper.selectBatchIds(furnitureIds)
                    .stream()
                    .collect(Collectors.toMap(Furniture::getId, f -> f));
            ctx.append("该用户已收藏以下商品：");
            ctx.append(favorites.stream()
                    .limit(5)
                    .map(fav -> {
                        Furniture f = furnitureMap.get(fav.getFurnitureId());
                        return f != null ? f.getFName() + "(ID:" + f.getId() + ")" : "";
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining("、")));
            ctx.append("。");
        }
        return ctx.toString();
    }

    /**
     * AI聊天请求数据传输对象，封装前端发送的聊天请求参数。
     *
     * @author 郭名城
     * @date 2026-07-30
     */
    @Data
    public static class ChatRequest {
        /**
         * 用户输入的消息内容
         */
        @NotBlank(message = "消息内容不能为空")
        private String message;
        /**
         * 会话唯一标识，首次请求时可为空，服务端将自动生成
         */
        private String conversationId;
    }

}
