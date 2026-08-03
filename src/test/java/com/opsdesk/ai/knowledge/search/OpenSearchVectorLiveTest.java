package com.opsdesk.ai.knowledge.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.config.EmbeddingProperties;
import com.opsdesk.ai.config.OpenSearchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** OpenSearch 真实向量 Mapping 创建与清理测试。 */
class OpenSearchVectorLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_OPENSEARCH_TEST", matches = "true")
    void shouldCreateVectorIndexWithDetectedDimensions() {
        OpenSearchProperties properties = new OpenSearchProperties();
        properties.setUrl("https://127.0.0.1:9200");
        properties.setUsername(System.getenv("OPENSEARCH_APP_USER"));
        properties.setPassword(System.getenv("OPENSEARCH_APP_PASSWORD"));
        String indexName = "opsdesk_knowledge_vector_probe_" + System.currentTimeMillis();
        OpenSearchKnowledgeRepository repository = new OpenSearchKnowledgeRepository(
                new ObjectMapper(), properties, new EmbeddingProperties());
        try {
            repository.createVectorIndex(indexName, 2304);
        } finally {
            repository.deleteIndex(indexName);
        }
    }
}
