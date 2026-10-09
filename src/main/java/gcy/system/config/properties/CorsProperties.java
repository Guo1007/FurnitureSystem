package gcy.system.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域白名单配置，对应 {@code app.cors.allowed-origins}（YAML 列表形式书写）。
 * <p>
 * 为何不用 {@code @Value} 直接绑 List：YAML 列表在 Environment 中会被展开成
 * {@code allowed-origins[0]}、{@code allowed-origins[1]} 这类带下标 key，而
 * {@code allowed-origins} 本身并不存在，{@code @Value} 只会命中默认空值，导致
 * 白名单静默失效、跨域请求被拒（403）；relaxed binding 的 {@code @ConfigurationProperties} 才能正确读取。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-30
 */
@Data
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * 允许跨域访问的来源列表；为空表示不放行任何跨域来源。
     */
    private List<String> allowedOrigins = new ArrayList<>();
}
