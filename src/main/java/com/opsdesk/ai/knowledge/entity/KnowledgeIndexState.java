package com.opsdesk.ai.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 知识文章索引状态实体。 */
@Getter
@Setter
public class KnowledgeIndexState {
    private Long id;
    private Long articleId;
    private Long articleVersion;
    private String contentHash;
    private String articleStatus;
    private String indexStatus;
    private String indexName;
    private String indexVersion;
    private Integer chunkCount;
    private String lastEventId;
    private LocalDateTime indexedTime;
    private Integer retryCount;
    private String lastError;
}
