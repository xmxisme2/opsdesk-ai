package com.opsdesk.ai.rag.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 单轮 RAG JSON 降级响应，不持久化会话。 */
public record RagChatResponse(String answer, boolean insufficientEvidence, List<RagReferenceVO> references,
                              String disclaimer, LocalDateTime generatedAt) { }
