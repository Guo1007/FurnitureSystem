package gcy.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝开放平台配置类。
 * <p>
 * 自动读取 application.yml 中 alipay 前缀的配置项，存放支付所需的应用ID、公私钥、
 * 网关地址与异步通知地址。默认网关指向沙箱环境，通过环境变量可切换为生产环境。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-22
 */
@Data
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /**
     * 支付宝应用 AppID（沙箱自带应用）
     */
    private String appId;

    /**
     * 应用私钥（签名请求，绝不可泄露）
     */
    private String appPrivateKey;

    /**
     * 支付宝公钥（验签回调通知）
     */
    private String alipayPublicKey;

    /**
     * 支付宝网关地址（沙箱/生产）
     */
    private String gateway;

    /**
     * 异步通知地址（必须是公网 HTTPS 且能被公网访问）
     */
    private String notifyUrl;

}