package com.opsdesk.ai.conversation.vo;

import java.time.LocalDateTime;

/** 会话历史列表项，ID 统一按字符串返回。 */
public record ConversationVO(String id, String title, String status, int messageCount,
                             LocalDateTime lastMessageTime, LocalDateTime createTime) { }
