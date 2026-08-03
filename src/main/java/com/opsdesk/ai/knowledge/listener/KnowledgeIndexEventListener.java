package com.opsdesk.ai.knowledge.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.knowledge.config.KnowledgeRabbitConfig;
import com.opsdesk.ai.knowledge.event.KnowledgeEventEnvelope;
import com.opsdesk.ai.knowledge.service.KnowledgeIndexEventProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** RabbitMQ 知识索引事件监听器；异常交由容器重试，耗尽后进入死信队列。 */
@Component
@ConditionalOnProperty(prefix = "opsdesk.ai", name = "indexing-enabled", havingValue = "true")
public class KnowledgeIndexEventListener {
    private final ObjectMapper objectMapper;
    private final KnowledgeIndexEventProcessor processor;

    public KnowledgeIndexEventListener(ObjectMapper objectMapper, KnowledgeIndexEventProcessor processor) {
        this.objectMapper = objectMapper;
        this.processor = processor;
    }

    @RabbitListener(queues = KnowledgeRabbitConfig.INDEX_QUEUE)
    public void onMessage(byte[] body) throws Exception {
        processor.process(objectMapper.readValue(body, KnowledgeEventEnvelope.class));
    }
}
