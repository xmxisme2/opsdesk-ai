package com.opsdesk.ai.knowledge.service;

import com.opsdesk.ai.embedding.EmbeddingGateway;
import com.opsdesk.ai.embedding.EmbeddingHealth;
import com.opsdesk.ai.knowledge.chunk.KnowledgeChunk;
import com.opsdesk.ai.knowledge.chunk.MarkdownChunker;
import com.opsdesk.ai.knowledge.client.KnowledgeSnapshot;
import com.opsdesk.ai.knowledge.search.OpenSearchKnowledgeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 负责文章分块、文档向量生成和 OpenSearch 写入的索引编排服务。 */
@Service
public class KnowledgeVectorIndexService {
    private final MarkdownChunker chunker;
    private final EmbeddingGateway embeddingGateway;
    private final OpenSearchKnowledgeRepository repository;

    public KnowledgeVectorIndexService(MarkdownChunker chunker,
                                       EmbeddingGateway embeddingGateway,
                                       OpenSearchKnowledgeRepository repository) {
        this.chunker = chunker;
        this.embeddingGateway = embeddingGateway;
        this.repository = repository;
    }

    public int index(KnowledgeSnapshot snapshot) {
        return indexInto(snapshot, repository.writeAlias());
    }

    public int indexInto(KnowledgeSnapshot snapshot, String targetIndex) {
        List<KnowledgeChunk> chunks = chunker.chunk(snapshot);
        List<float[]> vectors = embeddingGateway.embedDocuments(
                chunks.stream().map(KnowledgeChunk::embeddingText).toList());
        repository.indexChunks(snapshot, chunks, vectors, targetIndex);
        return chunks.size();
    }

    /** 新建向量索引前执行真实探测，并以实际维度固化 Mapping。 */
    public int createVectorIndex(String indexName) {
        EmbeddingHealth health = embeddingGateway.checkHealth();
        if (!health.success() || health.dimensions() <= 0) {
            throw new IllegalStateException("Embedding 健康检查失败：" + health.message());
        }
        repository.createVectorIndex(indexName, health.dimensions());
        return health.dimensions();
    }
}
