package com.opsdesk.ai.knowledge.chunk;

/** 知识文章的稳定 Markdown 分块。 */
public record KnowledgeChunk(
        int chunkNo,
        String heading,
        String content,
        String embeddingText,
        String contentHash
) {
}
