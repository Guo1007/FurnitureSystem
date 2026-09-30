package gcy.system.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域白名单配置。
 * <p>
 * 对应 {@code app.cors.allowed-origins}，在 application.yml 中以 YAML 列表形式书写：
 * <pre>
 * app:
 *   cors:
 *     allowed-origins:
 *       - http://localhost:5173
 *       - https://mingcheng.asia
 * </pre>
 * </p>
 * <p>
 * 【为什么不用 @Value 直接绑 List】
 * YAML 列表在 Environment 中会被展开成 {@code app.cors.allowed-origins[0]}、
 * {@code app.cors.allowed-origins[1]}……这类带下标的 key，
 * 而 {@code app.cors.allowed-origins} 这个 key 本身并不存在。
 * {@code @Value("${app.cors.allowed-origins:}") List<String>} 因此永远命中默认值（空），
 * 白名单静默失效、所有跨域请求被拒（403 Invalid CORS request）。
 * 改用 relaxed binding 的 {@code @ConfigurationProperties} 才能正确读取 YAML 列表。
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
