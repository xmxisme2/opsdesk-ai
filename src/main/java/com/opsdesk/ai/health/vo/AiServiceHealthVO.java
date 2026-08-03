package com.opsdesk.ai.health.vo;

import java.time.OffsetDateTime;

/**
 * 主应用读取的 AI 服务健康摘要。
 *
 * @param status               服务总体状态
 * @param service              服务名
 * @param aiEnabled            AI 总开关最终状态
 * @param ragEnabled           RAG 开关最终状态
 * @param databaseStatus       独立数据库状态
 * @param redisStatus          Redis 状态
 * @param serviceJwtConfigured Service JWT 是否已安全配置
 * @param checkedAt            检查时间
 */
public record AiServiceHealthVO(
        String status,
        String service,
        boolean aiEnabled,
        boolean ragEnabled,
        String databaseStatus,
        String redisStatus,
        boolean serviceJwtConfigured,
        OffsetDateTime checkedAt
) {
}
