package com.opsdesk.ai.knowledge.service;

import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.common.id.LocalSnowflakeIdGenerator;
import com.opsdesk.ai.config.OpenSearchProperties;
import com.opsdesk.ai.knowledge.client.KnowledgeSnapshot;
import com.opsdesk.ai.knowledge.client.KnowledgeSnapshotClient;
import com.opsdesk.ai.knowledge.entity.EventConsumptionLog;
import com.opsdesk.ai.knowledge.entity.KnowledgeIndexState;
import com.opsdesk.ai.knowledge.event.KnowledgeEventEnvelope;
import com.opsdesk.ai.knowledge.mapper.EventConsumptionLogMapper;
import com.opsdesk.ai.knowledge.mapper.KnowledgeIndexStateMapper;
import com.opsdesk.ai.knowledge.search.OpenSearchKnowledgeRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 知识文章事件索引处理器。
 *
 * <p>通过事件 ID 幂等，并以 aggregateVersion 拒绝旧事件覆盖新索引状态。</p>
 */
@Service
public class KnowledgeIndexEventProcessor {
    private static final String CONSUMER_NAME = "opsdesk-ai-knowledge-index-v1";
    private static final Set<String> UPSERT_EVENTS = Set.of(
            "KnowledgeArticlePublished", "KnowledgeArticleUpdated", "KnowledgeArticleReindexRequested");
    private static final Set<String> REMOVE_EVENTS = Set.of(
            "KnowledgeArticleOffline", "KnowledgeArticleDeleted");

    private final EventConsumptionLogMapper logMapper;
    private final KnowledgeIndexStateMapper stateMapper;
    private final KnowledgeSnapshotClient snapshotClient;
    private final OpenSearchKnowledgeRepository repository;
    private final KnowledgeVectorIndexService vectorIndexService;
    private final OpenSearchProperties openSearchProperties;
    private final LocalSnowflakeIdGenerator idGenerator;

    public KnowledgeIndexEventProcessor(EventConsumptionLogMapper logMapper,
                                        KnowledgeIndexStateMapper stateMapper,
                                        KnowledgeSnapshotClient snapshotClient,
                                        OpenSearchKnowledgeRepository repository,
                                        KnowledgeVectorIndexService vectorIndexService,
                                        OpenSearchProperties openSearchProperties,
                                        LocalSnowflakeIdGenerator idGenerator) {
        this.logMapper = logMapper;
        this.stateMapper = stateMapper;
        this.snapshotClient = snapshotClient;
        this.repository = repository;
        this.vectorIndexService = vectorIndexService;
        this.openSearchProperties = openSearchProperties;
        this.idGenerator = idGenerator;
    }

    @Transactional(rollbackFor = Exception.class)
    public void process(KnowledgeEventEnvelope event) {
        validate(event);
        EventConsumptionLog existingLog = logMapper.findByEventId(event.eventId());
        if (existingLog != null && "SUCCESS".equals(existingLog.getConsumeStatus())) {
            return;
        }
        if (existingLog == null) {
            insertLog(event);
        } else {
            logMapper.markProcessing(event.eventId());
        }

        long articleId = parseArticleId(event.aggregateId());
        KnowledgeIndexState state = stateMapper.findByArticleId(articleId);
        if (state != null && state.getArticleVersion() != null
                && state.getArticleVersion() >= event.aggregateVersion()) {
            logMapper.markSuccess(event.eventId());
            return;
        }
        if (UPSERT_EVENTS.contains(event.eventType())) {
            processUpsert(event, state, articleId);
        } else if (REMOVE_EVENTS.contains(event.eventType())) {
            repository.remove(event.aggregateId());
            saveState(state, articleId, event.aggregateVersion(), eventData(event, "contentHash"),
                    eventData(event, "status"), "REMOVED", 0, event.eventId());
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的知识事件类型");
        }
        logMapper.markSuccess(event.eventId());
    }

    private void processUpsert(KnowledgeEventEnvelope event, KnowledgeIndexState state, long articleId) {
        KnowledgeSnapshot snapshot = snapshotClient.fetch(
                event.aggregateId(), event.aggregateVersion(), event.eventId(), event.traceId());
        if (!"PUBLISHED".equals(snapshot.status())) {
            repository.remove(event.aggregateId());
            saveState(state, articleId, snapshot.version(), snapshot.contentHash(),
                    snapshot.status(), "REMOVED", 0, event.eventId());
            return;
        }
        int chunkCount = vectorIndexService.index(snapshot);
        saveState(state, articleId, snapshot.version(), snapshot.contentHash(),
                snapshot.status(), "INDEXED", chunkCount, event.eventId());
    }

    private void saveState(KnowledgeIndexState state, long articleId, long version, String contentHash,
                           String articleStatus, String indexStatus, int chunkCount, String eventId) {
        KnowledgeIndexState target = state == null ? new KnowledgeIndexState() : state;
        if (state == null) {
            target.setId(idGenerator.nextId());
            target.setArticleId(articleId);
        }
        target.setArticleVersion(version);
        target.setContentHash(contentHash == null ? "" : contentHash);
        target.setArticleStatus(articleStatus == null ? "" : articleStatus);
        target.setIndexStatus(indexStatus);
        target.setIndexName(openSearchProperties.getIndexName());
        target.setIndexVersion("HYBRID_V1");
        target.setChunkCount(chunkCount);
        target.setLastEventId(eventId);
        target.setIndexedTime(LocalDateTime.now());
        if (state == null) {
            stateMapper.insert(target);
        } else {
            stateMapper.update(target);
        }
    }

    private void insertLog(KnowledgeEventEnvelope event) {
        EventConsumptionLog log = new EventConsumptionLog();
        log.setId(idGenerator.nextId());
        log.setEventId(event.eventId());
        log.setEventType(event.eventType());
        log.setAggregateId(parseArticleId(event.aggregateId()));
        log.setConsumerName(CONSUMER_NAME);
        log.setConsumeStatus("PROCESSING");
        try {
            logMapper.insert(log);
        } catch (DuplicateKeyException exception) {
            EventConsumptionLog concurrent = logMapper.findByEventId(event.eventId());
            if (concurrent != null && "SUCCESS".equals(concurrent.getConsumeStatus())) {
                return;
            }
            logMapper.markProcessing(event.eventId());
        }
    }

    private void validate(KnowledgeEventEnvelope event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()
                || !"1.0".equals(event.eventVersion())
                || !"opsdesk-backend".equals(event.source())
                || !"KNOWLEDGE_ARTICLE".equals(event.aggregateType())
                || event.aggregateVersion() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "知识事件协议无效");
        }
    }

    private long parseArticleId(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "知识事件文章 ID 无效");
        }
    }

    private String eventData(KnowledgeEventEnvelope event, String field) {
        return event.data() == null ? null : event.data().path(field).asText(null);
    }
}
