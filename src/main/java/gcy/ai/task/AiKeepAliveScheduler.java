package gcy.ai.task;

import gcy.ai.aiservice.PingAiService;
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
 * <p>
 * 心跳走独立的 {@link PingAiService}：不写会话记忆、不做 RAG 检索，避免把
 * 整段知识库文本注入探针请求。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiKeepAliveScheduler {

    private final PingAiService pingAiService;

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
            List<String> parts = pingAiService
                    .ping("ping")
                    .collectList()
                    .block(Duration.ofSeconds(30));
            log.info("AI保活心跳成功: {}", String.join("", parts));
        } catch (Exception e) {
            log.warn("AI保活心跳失败（下次调度将继续尝试）: {}", e.getMessage());
        }
    }
}