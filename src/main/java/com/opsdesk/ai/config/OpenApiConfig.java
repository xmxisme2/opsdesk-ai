package com.opsdesk.ai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务 OpenAPI 文档配置。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI opsdeskAiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("OpsDesk AI Service API")
                .version("v1")
                .description("OpsDesk 独立 AI/RAG 服务内部接口"));
    }
}
