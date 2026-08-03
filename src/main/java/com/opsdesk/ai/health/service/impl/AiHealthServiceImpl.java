package com.opsdesk.ai.health.service.impl;

import com.opsdesk.ai.config.AiFeatureProperties;
import com.opsdesk.ai.config.entity.AiRuntimeConfig;
import com.opsdesk.ai.config.mapper.AiRuntimeConfigMapper;
import com.opsdesk.ai.health.service.AiHealthService;
import com.opsdesk.ai.health.vo.AiServiceHealthVO;
import com.opsdesk.ai.security.ServiceJwtVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * AI 服务健康检查实现。
 *
 * <p>只返回依赖状态和非敏感开关，不返回连接串、账号或密钥。</p>
 */
@Service
@Slf4j
public class AiHealthServiceImpl implements AiHealthService {

    /** 数据库总开关配置键：必须与初始化 SQL 保持一致。 */
    private static final String AI_ENABLED_CONFIG_KEY = "ai.enabled";
    /** 数据库 RAG 开关配置键：必须与初始化 SQL 保持一致。 */
    private static final String RAG_ENABLED_CONFIG_KEY = "ai.rag.enabled";

    private final AiRuntimeConfigMapper configMapper;
    private final RedisConnectionFactory redisConnectionFactory;
    private final AiFeatureProperties featureProperties;
    private final ServiceJwtVerifier serviceJwtVerifier;

    public AiHealthServiceImpl(AiRuntimeConfigMapper configMapper,
                               RedisConnectionFactory redisConnectionFactory,
                               AiFeatureProperties featureProperties,
                               ServiceJwtVerifier serviceJwtVerifier) {
        this.configMapper = configMapper;
        this.redisConnectionFactory = redisConnectionFactory;
        this.featureProperties = featureProperties;
        this.serviceJwtVerifier = serviceJwtVerifier;
    }

    @Override
    public AiServiceHealthVO check() {
        DependencyState database = checkDatabase();
        DependencyState redis = checkRedis();
        boolean aiEnabled = featureProperties.isEnabled() && database.aiEnabled();
        boolean ragEnabled = aiEnabled && featureProperties.isRagEnabled() && database.ragEnabled();
        boolean ready = database.up() && redis.up() && serviceJwtVerifier.isSecretConfigured();
        return new AiServiceHealthVO(
                ready ? "UP" : "DEGRADED",
                "opsdesk-ai-service",
                aiEnabled,
                ragEnabled,
                database.up() ? "UP" : "DOWN",
                redis.up() ? "UP" : "DOWN",
                serviceJwtVerifier.isSecretConfigured(),
                OffsetDateTime.now()
        );
    }

    private DependencyState checkDatabase() {
        try {
            AiRuntimeConfig aiEnabled = configMapper.selectActiveByKey(AI_ENABLED_CONFIG_KEY);
            AiRuntimeConfig ragEnabled = configMapper.selectActiveByKey(RAG_ENABLED_CONFIG_KEY);
            return new DependencyState(
                    true,
                    isTrue(aiEnabled),
                    isTrue(ragEnabled)
            );
        } catch (Exception exception) {
            log.warn("AI 独立数据库健康检查失败：{}", exception.getClass().getSimpleName());
            return DependencyState.down();
        }
    }

    private DependencyState checkRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            return new DependencyState("PONG".equalsIgnoreCase(pong), false, false);
        } catch (Exception exception) {
            log.warn("AI Redis 健康检查失败：{}", exception.getClass().getSimpleName());
            return DependencyState.down();
        }
    }

    private boolean isTrue(AiRuntimeConfig config) {
        return config != null && Boolean.parseBoolean(config.getConfigValue());
    }

    /**
     * 内部依赖检查结果，不对接口直接暴露异常信息。
     */
    private record DependencyState(boolean up, boolean aiEnabled, boolean ragEnabled) {
        private static DependencyState down() {
            return new DependencyState(false, false, false);
        }
    }
}
