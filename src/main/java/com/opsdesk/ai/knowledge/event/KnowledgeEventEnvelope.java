package com.opsdesk.ai.knowledge.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/** RabbitMQ 知识文章事件信封。 */
public record KnowledgeEventEnvelope(
        String eventId,
        String eventType,
        String eventVersion,
        String source,
        OffsetDateTime occurredAt,
        String traceId,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        String operatorId,
        JsonNode data
) {
}
