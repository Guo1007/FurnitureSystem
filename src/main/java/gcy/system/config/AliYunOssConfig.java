package gcy.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云OSS配置类。
 * <p>
 * 自动读取 application.yml 中 aliyun.oss 前缀的配置项，
 * 包括 Endpoint、AccessKey、Bucket 等信息，用于文件上传到阿里云OSS。
 * </p>
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
