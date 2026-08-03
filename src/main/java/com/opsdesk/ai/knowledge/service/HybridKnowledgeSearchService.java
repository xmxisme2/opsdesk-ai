package com.opsdesk.ai.knowledge.service;

import com.opsdesk.ai.config.OpenSearchProperties;
import com.opsdesk.ai.embedding.EmbeddingGateway;
import com.opsdesk.ai.knowledge.search.KnowledgeSearchHit;
import com.opsdesk.ai.knowledge.search.OpenSearchKnowledgeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BM25 与向量候选的加权 RRF 融合服务。
 *
 * <p>融合后限制每篇文章最多两个分块，并对低证据结果执行统一阈值过滤。</p>
 */
@Service
public class HybridKnowledgeSearchService {
    private static final int RRF_K = 60;
    private final OpenSearchKnowledgeRepository repository;
    private final EmbeddingGateway embeddingGateway;
    private final OpenSearchProperties properties;

    public HybridKnowledgeSearchService(OpenSearchKnowledgeRepository repository,
                                        EmbeddingGateway embeddingGateway,
                                        OpenSearchProperties properties) {
        this.repository = repository;
        this.embeddingGateway = embeddingGateway;
        this.properties = properties;
    }

    public List<KnowledgeSearchHit> search(String keyword, int requestedSize) {
        int finalSize = Math.min(Math.max(requestedSize, 1), 20);
        int candidateSize = Math.max(finalSize, properties.getHybridCandidateSize());
        List<KnowledgeSearchHit> keywordHits = repository.searchKeyword(keyword, candidateSize);
        List<KnowledgeSearchHit> vectorHits = repository.searchVector(
                embeddingGateway.embedQuery(keyword), candidateSize);
        Map<String, MutableHit> merged = new LinkedHashMap<>();
        addRanks(merged, keywordHits, true);
        addRanks(merged, vectorHits, false);

        List<KnowledgeSearchHit> ranked = merged.values().stream()
                .map(this::toResult)
                .filter(hit -> hit.score() >= properties.getHybridMinScore())
                .sorted(Comparator.comparingDouble(KnowledgeSearchHit::score).reversed())
                .toList();
        Map<String, Integer> articleCounts = new HashMap<>();
        List<KnowledgeSearchHit> results = new ArrayList<>(finalSize);
        for (KnowledgeSearchHit hit : ranked) {
            int count = articleCounts.getOrDefault(hit.articleId(), 0);
            if (count >= properties.getMaxChunksPerArticle()) {
                continue;
            }
            results.add(hit);
            articleCounts.put(hit.articleId(), count + 1);
            if (results.size() >= finalSize) {
                break;
            }
        }
        return results;
    }

    private void addRanks(Map<String, MutableHit> merged, List<KnowledgeSearchHit> hits, boolean keyword) {
        for (int index = 0; index < hits.size(); index++) {
            KnowledgeSearchHit hit = hits.get(index);
            MutableHit target = merged.computeIfAbsent(hit.chunkId(), ignored -> new MutableHit(hit));
            if (keyword) {
                target.keywordRank = index + 1;
                target.keywordScore = hit.keywordScore();
            } else {
                target.vectorRank = index + 1;
                target.vectorScore = hit.vectorScore();
            }
        }
    }

    /** 乘以 RRF_K + 1 后将首位单路命中归一到该路权重，最终分数范围为 0 至 1。 */
    private KnowledgeSearchHit toResult(MutableHit value) {
        double keywordRrf = value.keywordRank == 0 ? 0
                : properties.getKeywordWeight() * (RRF_K + 1.0) / (RRF_K + value.keywordRank);
        double vectorRrf = value.vectorRank == 0 ? 0
                : properties.getVectorWeight() * (RRF_K + 1.0) / (RRF_K + value.vectorRank);
        KnowledgeSearchHit source = value.source;
        return new KnowledgeSearchHit(source.articleId(), source.articleVersion(), source.chunkId(),
                source.title(), source.heading(), source.content(), value.keywordScore, value.vectorScore,
                keywordRrf + vectorRrf);
    }

    private static final class MutableHit {
        private final KnowledgeSearchHit source;
        private int keywordRank;
        private int vectorRank;
        private double keywordScore;
        private double vectorScore;

        private MutableHit(KnowledgeSearchHit source) {
            this.source = source;
        }
    }
}
