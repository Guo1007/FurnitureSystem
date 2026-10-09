package gcy.system.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Knife4j 接口文档自定义配置。
 * <p>
 * 首页描述取自 classpath:knife4jDoc/home.md（Markdown），由 Knife4j 前端渲染为富文本介绍。
 *
 * @author 郭名城
 * @date 2026-08-06
 */
@Slf4j
@Configuration
public class Knife4jConfig {

    /**
     * 首页介绍 Markdown 文档。
     */
    @Value("classpath:knife4jDoc/home.md")
    private Resource homeDoc;

    /**
     * 自定义 OpenAPI 文档元信息。
     */
    @Bean
    public OpenAPI furnitureOpenAPI() {
        log.info("初始化 OpenAPI 文档元信息：标题=家具商城 API 接口文档，版本=v1.1.0");
        return new OpenAPI()
                .info(new Info()
                        .title("家具商城 API 接口文档")
                        .description(readHomeDoc())
                        .version("v1.1.0")
                        .contact(new Contact()
                                .name("郭名城")
                                .email("guochengyang@example.com")));
    }

    /**
     * 读取首页 Markdown 文档，失败时返回降级文案。
     */
    private String readHomeDoc() {
        try {
            String content = new String(homeDoc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("读取 Knife4j 首页 Markdown 文档成功，长度={}", content.length());
            return content;
        } catch (IOException e) {
            log.error("读取 Knife4j 首页 Markdown 文档失败", e);
            return "## 家具商城 API 接口文档\n\n接口文档加载失败，请检查 classpath:doc/home.md 文件是否存在。";
        }
    }
}
