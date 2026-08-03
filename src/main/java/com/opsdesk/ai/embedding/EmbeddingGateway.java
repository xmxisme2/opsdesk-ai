package com.opsdesk.ai.embedding;

import java.util.List;

/** RAG 领域使用的向量模型网关，显式区分查询文本和文档文本。 */
public interface EmbeddingGateway {
    /** 为索引文档批量生成向量。 */
    List<float[]> embedDocuments(List<String> texts);

    /** 为用户查询生成向量。 */
    float[] embedQuery(String text);

    /** 发起不包含业务数据的真实探测，返回模型状态。 */
    EmbeddingHealth checkHealth();
}
