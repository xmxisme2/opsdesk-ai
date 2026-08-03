package com.opsdesk.ai.knowledge.search;

/** BM25 与向量混合检索命中。 */
public record KnowledgeSearchHit(
        String articleId,
        long articleVersion,
        String chunkId,
        String title,
        String heading,
        String content,
        double keywordScore,
        double vectorScore,
        double score
) {
}
