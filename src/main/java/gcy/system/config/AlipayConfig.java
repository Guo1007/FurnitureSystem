package gcy.system.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝客户端配置类。
 * <p>
 * 基于 {@link AlipayProperties} 构建全局唯一的 {@link AlipayClient}，
 * 统一封装应用ID、应用私钥、支付宝公钥、格式与签名方式（RSA2）。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-22
 */
@Configuration
@RequiredArgsConstructor
public class AlipayConfig {

    private final AlipayProperties alipayProperties;

    /**
     * 构建支付宝客户端 Bean。
     *
     * @return 配置完成的 AlipayClient 实例
     */
    @Bean
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(
                alipayProperties.getGateway(),
                alipayProperties.getAppId(),
                alipayProperties.getAppPrivateKey(),
                "json",
                "UTF-8",
                alipayProperties.getAlipayPublicKey(),
                "RSA2"
        );
    }

}