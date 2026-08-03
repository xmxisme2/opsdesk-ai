package com.opsdesk.ai.knowledge.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 知识索引事件 RabbitMQ 拓扑。 */
@Configuration
public class KnowledgeRabbitConfig {
    public static final String DOMAIN_EXCHANGE = "opsdesk.domain.events";
    public static final String DEAD_EXCHANGE = "opsdesk.domain.events.dlx";
    public static final String INDEX_QUEUE = "opsdesk.ai.knowledge-index";
    public static final String DEAD_QUEUE = "opsdesk.ai.knowledge-index.dlq";
    private static final String DEAD_ROUTING_KEY = "knowledge.index.dead";

    @Bean
    public TopicExchange domainEventExchange() {
        return new TopicExchange(DOMAIN_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange domainDeadLetterExchange() {
        return new TopicExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue knowledgeIndexQueue() {
        return QueueBuilder.durable(INDEX_QUEUE)
                .quorum()
                .withArgument("x-delivery-limit", 5)
                .withArgument("x-dead-letter-exchange", DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue knowledgeIndexDeadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).quorum().build();
    }

    @Bean
    public Binding knowledgeIndexBinding(Queue knowledgeIndexQueue, TopicExchange domainEventExchange) {
        return BindingBuilder.bind(knowledgeIndexQueue).to(domainEventExchange).with("knowledge.article.*");
    }

    @Bean
    public Binding knowledgeIndexDeadBinding(Queue knowledgeIndexDeadQueue,
                                             TopicExchange domainDeadLetterExchange) {
        return BindingBuilder.bind(knowledgeIndexDeadQueue)
                .to(domainDeadLetterExchange)
                .with(DEAD_ROUTING_KEY);
    }
}
