package com.opsdesk.ai.rag.service;

import com.opsdesk.ai.common.id.LocalSnowflakeIdGenerator;
import com.opsdesk.ai.config.ChatProperties;
import com.opsdesk.ai.rag.entity.AiCallLog;
import com.opsdesk.ai.rag.mapper.AiCallLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** AI 调用审计服务；审计落库失败不得影响用户获得已经生成的回答。 */
@Service
public class AiCallAuditService {
    private static final Logger LOG = LoggerFactory.getLogger(AiCallAuditService.class);
    private final LocalSnowflakeIdGenerator idGenerator;
    private final AiCallLogMapper mapper;
    private final ChatProperties properties;
    public AiCallAuditService(LocalSnowflakeIdGenerator idGenerator, AiCallLogMapper mapper, ChatProperties properties) {
        this.idGenerator = idGenerator; this.mapper = mapper; this.properties = properties;
    }
    public long record(String requestId, String traceId, String userId, long conversationId,
                       long retrievalMs, long generationMs, int candidates, int selected,
                       boolean insufficient, boolean success, String fallbackReason) {
        long id = idGenerator.nextId();
        try {
            AiCallLog log = new AiCallLog();
            log.setId(id); log.setRequestId(requestId); log.setTraceId(traceId == null ? "" : traceId);
            log.setScene("KNOWLEDGE_RAG"); log.setOperatorId(Long.parseLong(userId)); log.setProvider("deepseek");
            log.setConversationId(conversationId);
            log.setModel(properties.getModel()); log.setRetrievalDurationMs(retrievalMs); log.setGenerationDurationMs(generationMs);
            log.setDurationMs(retrievalMs + generationMs); log.setCandidateCount(candidates); log.setSelectedChunkCount(selected);
            log.setReferenceCount(selected); log.setDesensitized(true); log.setInsufficientEvidence(insufficient); log.setSuccess(success);
            log.setFallbackReason(fallbackReason); mapper.insert(log);
        } catch (Exception exception) { LOG.warn("AI 调用审计写入失败 requestId={}", requestId); }
        return id;
    }
}
