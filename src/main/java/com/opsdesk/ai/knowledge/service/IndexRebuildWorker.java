package com.opsdesk.ai.knowledge.service;

import com.opsdesk.ai.knowledge.client.KnowledgeSnapshot;
import com.opsdesk.ai.knowledge.client.KnowledgeSnapshotClient;
import com.opsdesk.ai.knowledge.mapper.AiIndexTaskMapper;
import com.opsdesk.ai.knowledge.mapper.KnowledgeIndexStateMapper;
import com.opsdesk.ai.knowledge.entity.KnowledgeIndexState;
import com.opsdesk.ai.common.id.LocalSnowflakeIdGenerator;
import com.opsdesk.ai.config.OpenSearchProperties;
import com.opsdesk.ai.knowledge.search.OpenSearchKnowledgeRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/** 异步构建新索引并原子切换别名的执行器。 */
@Service
public class IndexRebuildWorker {
    private final AiIndexTaskMapper taskMapper;
    private final KnowledgeSnapshotClient snapshotClient;
    private final OpenSearchKnowledgeRepository repository;
    private final KnowledgeVectorIndexService vectorIndexService;
    private final KnowledgeIndexStateMapper stateMapper;
    private final LocalSnowflakeIdGenerator idGenerator;
    private final OpenSearchProperties openSearchProperties;

    public IndexRebuildWorker(AiIndexTaskMapper taskMapper,
                              KnowledgeSnapshotClient snapshotClient,
                              OpenSearchKnowledgeRepository repository,
                              KnowledgeVectorIndexService vectorIndexService,
                              KnowledgeIndexStateMapper stateMapper,
                              LocalSnowflakeIdGenerator idGenerator,
                              OpenSearchProperties openSearchProperties) {
        this.taskMapper = taskMapper;
        this.snapshotClient = snapshotClient;
        this.repository = repository;
        this.vectorIndexService = vectorIndexService;
        this.stateMapper = stateMapper;
        this.idGenerator = idGenerator;
        this.openSearchProperties = openSearchProperties;
    }

    /** 对账当前已发布文章与索引状态，补建缺失项并移除已不再发布的残留文档。 */
    @Async
    public void reconcile(Long taskId) {
        int total = 0;
        int success = 0;
        try {
            taskMapper.markRunning(taskId, openSearchProperties.getWriteAlias());
            Set<Long> publishedIds = new HashSet<>();
            String afterId = "0";
            boolean hasMore;
            do {
                KnowledgeSnapshotClient.SnapshotPage page =
                        snapshotClient.fetchPublishedPage(afterId, 100, "");
                total += page.items().size();
                for (KnowledgeSnapshot snapshot : page.items()) {
                    int chunkCount = vectorIndexService.index(snapshot);
                    long articleId = Long.parseLong(snapshot.articleId());
                    publishedIds.add(articleId);
                    saveIndexedState(snapshot, taskId, chunkCount);
                    success++;
                }
                afterId = page.nextAfterId();
                hasMore = page.hasMore();
            } while (hasMore);
            for (KnowledgeIndexState state : stateMapper.findAll()) {
                if (!publishedIds.contains(state.getArticleId()) && !"REMOVED".equals(state.getIndexStatus())) {
                    repository.remove(String.valueOf(state.getArticleId()));
                    state.setArticleStatus("NOT_PUBLISHED");
                    state.setIndexStatus("REMOVED");
                    state.setChunkCount(0);
                    state.setLastEventId("reconcile-" + taskId);
                    state.setIndexedTime(LocalDateTime.now());
                    stateMapper.update(state);
                }
            }
            taskMapper.markSuccess(taskId, total, success);
        } catch (Exception exception) {
            taskMapper.markFailed(taskId, total, success, safeError(exception));
        }
    }

    private void saveIndexedState(KnowledgeSnapshot snapshot, Long taskId, int chunkCount) {
        long articleId = Long.parseLong(snapshot.articleId());
        KnowledgeIndexState state = stateMapper.findByArticleId(articleId);
        if (state == null) {
            state = new KnowledgeIndexState();
            state.setId(idGenerator.nextId());
            state.setArticleId(articleId);
        }
        state.setArticleVersion(snapshot.version());
        state.setContentHash(snapshot.contentHash());
        state.setArticleStatus(snapshot.status());
        state.setIndexStatus("INDEXED");
        state.setIndexName(openSearchProperties.getWriteAlias());
        state.setIndexVersion("HYBRID_V1");
        state.setChunkCount(chunkCount);
        state.setLastEventId("reconcile-" + taskId);
        state.setIndexedTime(LocalDateTime.now());
        if (stateMapper.findByArticleId(articleId) == null) {
            stateMapper.insert(state);
        } else {
            stateMapper.update(state);
        }
    }

    @Async
    public void rebuild(Long taskId) {
        String targetIndex = "opsdesk_knowledge_v"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int total = 0;
        int success = 0;
        try {
            taskMapper.markRunning(taskId, targetIndex);
            vectorIndexService.createVectorIndex(targetIndex);
            String afterId = "0";
            boolean hasMore;
            do {
                KnowledgeSnapshotClient.SnapshotPage page =
                        snapshotClient.fetchPublishedPage(afterId, 100, "");
                total += page.items().size();
                for (KnowledgeSnapshot snapshot : page.items()) {
                    vectorIndexService.indexInto(snapshot, targetIndex);
                    success++;
                }
                afterId = page.nextAfterId();
                hasMore = page.hasMore();
            } while (hasMore);
            repository.switchAliases(targetIndex);
            taskMapper.markSuccess(taskId, total, success);
        } catch (Exception exception) {
            taskMapper.markFailed(taskId, total, success, safeError(exception));
        }
    }

    private String safeError(Exception exception) {
        String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
