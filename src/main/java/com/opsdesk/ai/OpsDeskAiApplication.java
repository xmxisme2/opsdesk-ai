package com.opsdesk.ai;

import com.opsdesk.ai.config.AiFeatureProperties;
import com.opsdesk.ai.config.ChatProperties;
import com.opsdesk.ai.config.ServiceJwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * OpsDesk 独立 AI/RAG 服务启动入口。
 */
@SpringBootApplication
@EnableConfigurationProperties({AiFeatureProperties.class, ServiceJwtProperties.class, ChatProperties.class})
@EnableAsync
public class OpsDeskAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsDeskAiApplication.class, args);
    }
}
