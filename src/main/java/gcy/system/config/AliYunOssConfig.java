package gcy.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliYunOssConfig {

    /**
     * OSS服务端点地址
     */
    private String endpoint;

    /**
     * AccessKey ID
     */
    private String key;

    /**
     * AccessKey Secret
     */
    private String secret;

    /**
     * 存储空间名称
     */
    private String bucket;

    /**
     * OSS访问域名URL
     */
    private String url;

}
