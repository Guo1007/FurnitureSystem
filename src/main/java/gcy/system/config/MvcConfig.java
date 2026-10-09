package gcy.system.config;

import gcy.system.config.properties.CorsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * MVC 配置类：CORS 跨域映射与异步请求线程池。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class MvcConfig implements WebMvcConfigurer {

    /**
     * 跨域来源白名单，取自 {@code app.cors.allowed-origins}；空白时回落为「不放行任何跨域来源」。
     * <p>
     * 必须用 {@code @ConfigurationProperties} 绑定而非 {@code @Value}：YAML 列表在 Environment 中
     * 展开为带下标的 key，{@code @Value} 拿不到，会导致白名单静默为空、全站跨域 403。
     */
    private final CorsProperties corsProperties;

    /**
     * 构造注入跨域白名单配置。
     */
    public MvcConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 注册 CORS 映射：仅放行白名单来源，允许携带凭证，预检缓存 3600 秒。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>();
        if (corsProperties != null && corsProperties.getAllowedOrigins() != null) {
            for (String o : corsProperties.getAllowedOrigins()) {
                if (StringUtils.hasText(o)) {
                    origins.add(o.trim());
                }
            }
        }

        if (origins.isEmpty()) {
            // 未配置白名单时不允许任何跨域来源，避免退化成全通配
            log.warn("未配置 app.cors.allowed-origins，已禁用所有跨域来源；请在 application.yml 中补充前端域名");
            registry.addMapping("/**")
                    .allowedOrigins()
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
            return;
        }

        log.info("CORS 白名单已生效: {}", origins);
        registry.addMapping("/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 配置异步请求支持。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor());
        configurer.setDefaultTimeout(60_000);
    }

    /**
     * 应用中默认注入的异步任务执行器（{@link Primary}）。
     */
    @Bean
    @Primary
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }

    /**
     * MVC 异步请求专用线程池。
     */
    @Bean
    public ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mvc-async-");
        executor.initialize();
        return executor;
    }

}
