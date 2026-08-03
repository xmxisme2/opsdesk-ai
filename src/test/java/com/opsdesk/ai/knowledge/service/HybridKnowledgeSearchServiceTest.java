package com.opsdesk.ai.knowledge.service;

import com.opsdesk.ai.config.OpenSearchProperties;
import com.opsdesk.ai.embedding.EmbeddingGateway;
import com.opsdesk.ai.knowledge.search.KnowledgeSearchHit;
import com.opsdesk.ai.knowledge.search.OpenSearchKnowledgeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 混合检索融合、文章分块上限和低证据过滤测试。 */
class HybridKnowledgeSearchServiceTest {
    @Test
    void shouldFuseBothRecallListsAndLimitChunksPerArticle() {
        OpenSearchKnowledgeRepository repository = mock(OpenSearchKnowledgeRepository.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        OpenSearchProperties properties = new OpenSearchProperties();
        properties.setHybridMinScore(0.3);
        properties.setMaxChunksPerArticle(1);
        when(gateway.embedQuery(any())).thenReturn(new float[]{0.1f, 0.2f});
        when(repository.searchKeyword(any(), anyInt())).thenReturn(List.of(
                hit("1", "c1", 10, 0), hit("1", "c2", 9, 0), hit("2", "c3", 8, 0)));
        when(repository.searchVector(any(), anyInt())).thenReturn(List.of(
                hit("2", "c3", 0, 0.95), hit("1", "c1", 0, 0.9)));

        List<KnowledgeSearchHit> results = new HybridKnowledgeSearchService(repository, gateway, properties)
                .search("VPN 无法连接", 6);

        assertEquals(2, results.size());
        assertEquals("c1", results.get(0).chunkId());
        assertTrue(results.get(0).score() > results.get(1).score());
    }

    private KnowledgeSearchHit hit(String articleId, String chunkId, double keywordScore, double vectorScore) {
        return new KnowledgeSearchHit(articleId, 1, chunkId, "标题", "小节", "正文",
                keywordScore, vectorScore, 0);
    }
}
