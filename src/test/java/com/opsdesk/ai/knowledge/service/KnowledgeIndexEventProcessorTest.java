package com.opsdesk.ai.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 知识索引消费幂等和乱序保护测试。 */
class KnowledgeIndexEventProcessorTest {
    private EventConsumptionLogMapper logMapper;
    private KnowledgeIndexStateMapper stateMapper;
    private KnowledgeSnapshotClient snapshotClient;
    private OpenSearchKnowledgeRepository repository;
    private KnowledgeVectorIndexService vectorIndexService;
    private KnowledgeIndexEventProcessor processor;

    @BeforeEach
    void setUp() {
        logMapper = mock(EventConsumptionLogMapper.class);
        stateMapper = mock(KnowledgeIndexStateMapper.class);
        snapshotClient = mock(KnowledgeSnapshotClient.class);
        repository = mock(OpenSearchKnowledgeRepository.class);
        vectorIndexService = mock(KnowledgeVectorIndexService.class);
        OpenSearchProperties properties = new OpenSearchProperties();
        processor = new KnowledgeIndexEventProcessor(logMapper, stateMapper, snapshotClient, repository,
                vectorIndexService,
                properties, new LocalSnowflakeIdGenerator());
    }

    @Test
    void duplicateSuccessEventShouldReturnWithoutIndexing() {
        EventConsumptionLog log = new EventConsumptionLog();
        log.setConsumeStatus("SUCCESS");
        when(logMapper.findByEventId("event-1")).thenReturn(log);

        processor.process(event("event-1", 1));

        verifyNoInteractions(snapshotClient, repository, stateMapper);
    }

    @Test
    void olderEventShouldNotOverwriteNewerIndexState() {
        KnowledgeIndexState state = new KnowledgeIndexState();
        state.setArticleVersion(5L);
        when(stateMapper.findByArticleId(10L)).thenReturn(state);

        processor.process(event("event-2", 3));

        verifyNoInteractions(snapshotClient, repository);
        verify(logMapper).markSuccess("event-2");
    }

    @Test
    void publishedEventShouldFetchSnapshotAndPersistIndexedState() {
        KnowledgeSnapshot snapshot = new KnowledgeSnapshot(
                "10", 4, "VPN 排障", "摘要", "正文", "1", "网络",
                List.of(new KnowledgeSnapshot.Tag("2", "VPN")), null, "PUBLISHED",
                "ALL_AUTHENTICATED", List.of(), List.of(), LocalDateTime.now(), LocalDateTime.now(), "hash");
        when(snapshotClient.fetch("10", 4, "event-3", "trace")).thenReturn(snapshot);
        when(vectorIndexService.index(snapshot)).thenReturn(1);

        processor.process(event("event-3", 4));

        verify(vectorIndexService).index(snapshot);
        ArgumentCaptor<KnowledgeIndexState> stateCaptor = ArgumentCaptor.forClass(KnowledgeIndexState.class);
        verify(stateMapper).insert(stateCaptor.capture());
        assertEquals("INDEXED", stateCaptor.getValue().getIndexStatus());
        assertEquals(4L, stateCaptor.getValue().getArticleVersion());
        assertEquals(1, stateCaptor.getValue().getChunkCount());
        verify(logMapper).markSuccess("event-3");
    }

    private KnowledgeEventEnvelope event(String eventId, long version) {
        return new KnowledgeEventEnvelope(
                eventId, "KnowledgeArticlePublished", "1.0", "opsdesk-backend",
                OffsetDateTime.now(), "trace", "KNOWLEDGE_ARTICLE", "10", version, "1",
                new ObjectMapper().createObjectNode().put("status", "PUBLISHED").put("contentHash", "hash")
        );
    }
}
