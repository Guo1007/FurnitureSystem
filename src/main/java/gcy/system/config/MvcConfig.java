package gcy.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    /**
     * 配置跨域资源共享（CORS）映射规则。
     * <p>
     * 允许所有来源跨域访问，支持携带凭证（Cookie），并设置预检请求缓存时间为3600秒。
     *
     * @param registry CORS注册器，用于添加跨域映射规则
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
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
