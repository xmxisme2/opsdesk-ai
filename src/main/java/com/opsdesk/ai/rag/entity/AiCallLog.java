package com.opsdesk.ai.rag.entity;

import lombok.Data;

/** RAG 调用审计实体，不保存原始问题、知识片段或模型密钥。 */
@Data
public class AiCallLog {
    private Long id;
    private String requestId;
    private String traceId;
    private String scene;
    private Long operatorId;
    private Long conversationId;
    private String provider;
    private String model;
    private Long retrievalDurationMs;
    private Long generationDurationMs;
    private Long durationMs;
    private Integer candidateCount;
    private Integer selectedChunkCount;
    private Integer referenceCount;
    private Boolean desensitized;
    private Boolean insufficientEvidence;
    private Boolean success;
    private String errorCode;
    private String fallbackReason;
}
