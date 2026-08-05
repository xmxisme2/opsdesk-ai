package com.opsdesk.ai.conversation.model;

import lombok.Data;
import java.time.LocalDateTime;

/** AI 会话数据库行模型。 */
@Data
public class ConversationRow {
    private Long id;
    private Long ownerId;
    private String title;
    private String scene;
    private String status;
    private LocalDateTime lastMessageTime;
    private Integer messageCount;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
