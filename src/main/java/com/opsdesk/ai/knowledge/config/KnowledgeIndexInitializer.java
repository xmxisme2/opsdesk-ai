package com.opsdesk.ai.knowledge.config;

import com.opsdesk.ai.embedding.EmbeddingGateway;
import com.opsdesk.ai.embedding.EmbeddingHealth;
import com.opsdesk.ai.knowledge.search.OpenSearchKnowledgeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 索引消费启用时在启动阶段校验 OpenSearch Mapping 与别名。 */
@Component
@ConditionalOnProperty(prefix = "opsdesk.ai", name = "indexing-enabled", havingValue = "true")
public class KnowledgeIndexInitializer implements ApplicationRunner {
    private final OpenSearchKnowledgeRepository repository;
    private final EmbeddingGateway embeddingGateway;

    public KnowledgeIndexInitializer(OpenSearchKnowledgeRepository repository,
                                     EmbeddingGateway embeddingGateway) {
        this.repository = repository;
        this.embeddingGateway = embeddingGateway;
    }

    @Override
    public void run(ApplicationArguments args) {
        EmbeddingHealth health = embeddingGateway.checkHealth();
        if (!health.success() || health.dimensions() <= 0) {
            throw new IllegalStateException("索引消费启用但 Embedding 不可用：" + health.message());
        }
        repository.ensureVectorIndexAndAliases(health.dimensions());
    }
}
