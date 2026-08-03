package com.opsdesk.ai.embedding;

import com.opsdesk.ai.config.EmbeddingProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 基于 Spring AI 模型适配器的 Embedding 领域网关。 */
@Service
public class TencentEmbeddingGateway implements EmbeddingGateway {
    private static final String HEALTH_CHECK_TEXT = "OpsDesk 向量模型健康检查";
    private final TencentEmbeddingModel model;
    private final EmbeddingProperties properties;

    public TencentEmbeddingGateway(TencentEmbeddingModel model, EmbeddingProperties properties) {
        this.model = model;
        this.properties = properties;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return model.embedDocuments(texts);
    }

    @Override
    public float[] embedQuery(String text) {
        return model.embedQuery(text);
    }

    @Override
    public EmbeddingHealth checkHealth() {
        if (!properties.isConfigured()) {
            return new EmbeddingHealth(false, false, properties.getProvider(), properties.getModel(),
                    0, 0, "Embedding 模型密钥未配置");
        }
        Instant start = Instant.now();
        try {
            float[] vector = model.embedQuery(HEALTH_CHECK_TEXT);
            return new EmbeddingHealth(true, true, properties.getProvider(), properties.getModel(),
                    vector.length, Duration.between(start, Instant.now()).toMillis(), "模型调用成功");
        } catch (Exception exception) {
            return new EmbeddingHealth(true, false, properties.getProvider(), properties.getModel(),
                    0, Duration.between(start, Instant.now()).toMillis(), safeMessage(exception));
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "模型调用失败：" + exception.getClass().getSimpleName();
        }
        return message.length() <= 200 ? message : message.substring(0, 200);
    }
}
