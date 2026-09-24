package gcy.ai.task;

import gcy.ai.aiservice.FurnitureAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * AI服务保活调度器。
 * <p>
 * 定期向大模型发送轻量心跳请求，保持模型实例热态与本地HTTP连接池复用，
 * 消除长时间空闲后首次对话失败的冷启动问题
 * （DashScope侧模型实例空闲缩容 + OkHttp长连接闲置失效，两者叠加导致
 * 首次请求超时，第二次因已唤醒而成功）。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiKeepAliveScheduler {

    /**
     * 心跳使用的独立记忆桶ID，与用户会话记忆完全隔离
     */
    private static final String KEEPALIVE_MEMORY_ID = "keepalive:heartbeat";

    private final FurnitureAiService furnitureAiService;

    /**
     * 每15分钟发送一次心跳。
     * <p>
     * 初次延迟90秒，等待应用完全就绪后再开始保活；
     * 心跳超时上限30秒，防止卡死调度线程；
     * 心跳失败仅记录日志，不影响后续调度（下次心跳继续尝试，本身也起到重试作用）。
     * </p>
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 90 * 1000)
    public void heartbeat() {
        try {
            List<String> parts = furnitureAiService
                    .ping(KEEPALIVE_MEMORY_ID, "ping")
                    .collectList()
                    .block(Duration.ofSeconds(30));
            log.info("AI保活心跳成功: {}", String.join("", parts));
        } catch (Exception e) {
            log.warn("AI保活心跳失败（下次调度将继续尝试）: {}", e.getMessage());
        }
    }
}
