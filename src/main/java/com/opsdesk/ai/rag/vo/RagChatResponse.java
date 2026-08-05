package com.opsdesk.ai.rag.vo;

import java.time.LocalDateTime;
import java.util.List;

/** RAG JSON 降级响应，返回持久化会话和助手消息 ID。 */
public record RagChatResponse(String answer, String conversationId, String messageId,
                              boolean insufficientEvidence, List<RagReferenceVO> references,
                              String disclaimer, LocalDateTime generatedAt) { }
