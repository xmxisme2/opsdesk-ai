package com.opsdesk.ai.health.service.impl;

import com.opsdesk.ai.config.AiFeatureProperties;
import com.opsdesk.ai.config.entity.AiRuntimeConfig;
import com.opsdesk.ai.config.mapper.AiRuntimeConfigMapper;
import com.opsdesk.ai.health.vo.AiServiceHealthVO;
import com.opsdesk.ai.security.ServiceJwtVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 健康服务测试。
 */
class AiHealthServiceImplTest {

    @Test
    void shouldReportUpButKeepAiDisabled() {
        AiRuntimeConfigMapper mapper = mock(AiRuntimeConfigMapper.class);
        when(mapper.selectActiveByKey("ai.enabled")).thenReturn(booleanConfig(false));
        when(mapper.selectActiveByKey("ai.rag.enabled")).thenReturn(booleanConfig(false));

        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisConnection.ping()).thenReturn("PONG");
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        when(redisFactory.getConnection()).thenReturn(redisConnection);

        AiFeatureProperties featureProperties = new AiFeatureProperties();
        featureProperties.setEnabled(false);
        featureProperties.setRagEnabled(false);
        ServiceJwtVerifier verifier = mock(ServiceJwtVerifier.class);
        when(verifier.isSecretConfigured()).thenReturn(true);

        AiHealthServiceImpl service = new AiHealthServiceImpl(
                mapper,
                redisFactory,
                featureProperties,
                verifier
        );

        AiServiceHealthVO health = service.check();

        assertEquals("UP", health.status());
        assertEquals("UP", health.databaseStatus());
        assertEquals("UP", health.redisStatus());
        assertFalse(health.aiEnabled());
        assertFalse(health.ragEnabled());
    }

    private AiRuntimeConfig booleanConfig(boolean value) {
        AiRuntimeConfig config = new AiRuntimeConfig();
        config.setConfigValue(Boolean.toString(value));
        return config;
    }
}
