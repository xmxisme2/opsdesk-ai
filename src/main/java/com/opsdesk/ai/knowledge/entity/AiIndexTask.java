package com.opsdesk.ai.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

/** 全量重建与对账任务实体。 */
@Getter
@Setter
public class AiIndexTask {
    private Long id;
    private String taskType;
    private Long articleId;
    private String taskStatus;
    private String requestId;
    private String targetIndexName;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private String lastError;
}
