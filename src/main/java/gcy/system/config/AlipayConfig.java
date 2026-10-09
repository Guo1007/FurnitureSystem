package gcy.system.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝客户端配置：构建全局唯一的 {@link AlipayClient}。
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