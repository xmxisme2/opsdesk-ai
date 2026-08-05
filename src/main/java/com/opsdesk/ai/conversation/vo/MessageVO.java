package com.opsdesk.ai.conversation.vo;

import com.opsdesk.ai.rag.vo.RagReferenceVO;
import java.time.LocalDateTime;
import java.util.List;

/** 会话消息详情，助手消息附带调用时的引用快照和当前用户反馈。 */
public record MessageVO(String id, String role, String content, String status, boolean insufficientEvidence,
                        String feedback, LocalDateTime createTime, List<RagReferenceVO> references) { }
