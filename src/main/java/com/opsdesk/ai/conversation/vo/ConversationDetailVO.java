package com.opsdesk.ai.conversation.vo;

import java.util.List;

/** 当前用户自己的会话详情。 */
public record ConversationDetailVO(ConversationVO conversation, List<MessageVO> messages) { }
