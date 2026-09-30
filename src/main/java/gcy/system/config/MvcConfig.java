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
 * MVC配置类，负责配置Spring MVC的相关功能。
 * <p>
 * 配置内容主要包括：
 * <ul>
 *     <li>跨域资源共享（CORS）映射规则</li>
 *     <li>异步请求支持及对应的线程池</li>
 *     <li>默认异步任务执行器（主线程池）</li>
 *     <li>MVC异步请求专用线程池</li>
 * </ul>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class MvcConfig implements WebMvcConfigurer {

    /**
     * 允许跨域访问的来源白名单，取自 {@code app.cors.allowed-origins}。
     * <p>
     * 此前该配置已存在于 application.yml 但全项目零引用，实际生效的是 {@code allowedOriginPatterns("*")}
     * 且 {@code allowCredentials(true)}——等价于允许任意站点携带用户凭证调用本站接口，CSRF 防护形同虚设。
     * 这里把白名单真正接上；配置为空白时回落为「不放行任何跨域来源」，宁可不可用也不全通配。
     * </p>
     * <p>
     * 绑定方式必须用 {@code @ConfigurationProperties} 而非 {@code @Value}：
     * YAML 列表在 Environment 中会展开为带下标的 key，{@code @Value} 拿不到，
     * 曾因此导致白名单静默为空、全站跨域 403（详见 CorsProperties 说明）。
     * </p>
     */
    private final CorsProperties corsProperties;

    /**
     * 构造注入跨域白名单配置。
     *
     * @param corsProperties 跨域白名单配置，读取 {@code app.cors.allowed-origins}
     */
    public MvcConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 配置跨域资源共享（CORS）映射规则。
     * <p>
     * 仅放行 {@code app.cors.allowed-origins} 中显式配置的来源，支持携带凭证（Cookie），
     * 并设置预检请求缓存时间为3600秒。
     *
     * @param registry CORS注册器，用于添加跨域映射规则
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
     * 配置异步请求支持，设置MVC异步任务执行器及默认超时时间。
     *
     * @param configurer 异步支持配置器，用于设置任务执行器和超时时间
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor());
        configurer.setDefaultTimeout(60_000);
    }

    /**
     * 创建默认的异步任务执行器Bean（主线程池）。
     * <p>
     * 该执行器被标记为{@link Primary}，是应用中默认注入的{@link Executor}实例。
     * 核心线程数4，最大线程数8，队列容量200，线程名前缀为"async-"。
     *
     * @return 配置好的主线程池执行器
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
     * 创建MVC异步请求专用的线程池执行器Bean。
     * <p>
     * 该执行器专用于处理Spring MVC的异步请求，核心线程数2，最大线程数4，
     * 队列容量50，线程名前缀为"mvc-async-"。
     *
     * @return 配置好的MVC异步请求专用线程池执行器
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
