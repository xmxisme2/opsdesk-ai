package com.opsdesk.ai.conversation.model;

import lombok.Data;
import java.time.LocalDateTime;

/** AI 会话消息数据库行模型。 */
@Data
public class MessageRow {
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String contentHash;
    private Long callLogId;
    private Integer sequenceNo;
    private Boolean insufficientEvidence;
    private String status;
    private String feedback;
    private LocalDateTime createTime;
    private Long createBy;
}
