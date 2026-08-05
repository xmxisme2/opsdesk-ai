package com.opsdesk.ai.conversation.model;

import lombok.Data;

/** AI 调用引用快照数据库行模型。 */
@Data
public class ReferenceRow {
    private Long id;
    private Long callLogId;
    private Long articleId;
    private Long articleVersion;
    private String chunkId;
    private Integer chunkNo;
    private String title;
    private String heading;
    private String snippet;
    private Double keywordScore;
    private Double vectorScore;
    private Double finalScore;
    private Integer rankNo;
}
