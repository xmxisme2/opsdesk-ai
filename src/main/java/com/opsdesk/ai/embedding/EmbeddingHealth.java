package com.opsdesk.ai.embedding;

/** Embedding 真实健康检查结果，不包含密钥、原文或向量值。 */
public record EmbeddingHealth(
        boolean configured,
        boolean success,
        String provider,
        String model,
        int dimensions,
        long latencyMs,
        String message
) {
}
