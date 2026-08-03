package com.opsdesk.ai.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** RabbitMQ 事件消费幂等日志实体。 */
@Getter
@Setter
public class EventConsumptionLog {
    private Long id;
    private String eventId;
    private String eventType;
    private Long aggregateId;
    private String consumerName;
    private String consumeStatus;
    private Integer retryCount;
    private LocalDateTime consumedTime;
    private String lastError;
}
