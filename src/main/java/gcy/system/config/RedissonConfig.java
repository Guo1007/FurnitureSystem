package gcy.system.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置类，用于创建并管理 Redisson 客户端 Bean。
 * <p>
 * 该类从 Spring 配置中读取 Redis 的主机和端口信息，
 * 通过 {@link Bean} 注解将 {@link RedissonClient} 实例注册到 Spring 容器中，
 * 供其他组件注入并使用分布式锁、分布式集合等功能。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * 创建并配置 Redisson 客户端 Bean。
     * <p>
     * 使用单服务器模式连接 Redis，地址由配置文件中的
     * {@code spring.data.redis.host} 和 {@code spring.data.redis.port} 属性指定，
     * 默认值为 localhost:6379。
     * </p>
     *
     * @return 配置好的 {@link RedissonClient} 实例，用于与 Redis 交互
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }
}
