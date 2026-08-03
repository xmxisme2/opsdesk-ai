package com.opsdesk.ai.health.vo;

/** 模型或连接测试结果，禁止携带密钥、原文和向量。 */
public record AiConnectionTestVO(
        String target,
        boolean configured,
        boolean success,
        String provider,
        String model,
        Integer dimensions,
        long latencyMs,
        String message
) {
}
