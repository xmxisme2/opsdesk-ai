package com.opsdesk.ai.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 腾讯 Embedding 真实连通性测试。
 *
 * <p>仅在显式设置 RUN_LIVE_EMBEDDING_TEST=true 时运行，测试输出不包含密钥、文本和向量。</p>
 */
class TencentEmbeddingLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_EMBEDDING_TEST", matches = "true")
    void shouldReturnNonEmptyEmbedding() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setSecretId(System.getenv("TENCENT_SECRET_ID"));
        properties.setSecretKey(System.getenv("TENCENT_SECRET_KEY"));
        TencentEmbeddingGateway gateway = new TencentEmbeddingGateway(
                new TencentEmbeddingModel(new ObjectMapper(), properties), properties);

        EmbeddingHealth health = gateway.checkHealth();

        System.out.printf("Embedding health: success=%s, dimensions=%d, latencyMs=%d%n",
                health.success(), health.dimensions(), health.latencyMs());
        assertTrue(health.success(), health.message());
        assertTrue(health.dimensions() > 0);
    }
}
