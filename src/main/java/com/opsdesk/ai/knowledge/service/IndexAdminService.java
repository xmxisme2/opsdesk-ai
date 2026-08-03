package com.opsdesk.ai.knowledge.service;

import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.common.id.LocalSnowflakeIdGenerator;
import com.opsdesk.ai.knowledge.dto.IndexRebuildRequest;
import com.opsdesk.ai.knowledge.dto.IndexReconcileRequest;
import com.opsdesk.ai.knowledge.entity.AiIndexTask;
import com.opsdesk.ai.knowledge.mapper.AiIndexTaskMapper;
import com.opsdesk.ai.knowledge.vo.IndexTaskAcceptedVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 索引管理任务受理服务。 */
@Service
public class IndexAdminService {
    private final AiIndexTaskMapper taskMapper;
    private final LocalSnowflakeIdGenerator idGenerator;
    private final IndexRebuildWorker worker;

    public IndexAdminService(AiIndexTaskMapper taskMapper,
                             LocalSnowflakeIdGenerator idGenerator,
                             IndexRebuildWorker worker) {
        this.taskMapper = taskMapper;
        this.idGenerator = idGenerator;
        this.worker = worker;
    }

    @Transactional(rollbackFor = Exception.class)
    public IndexTaskAcceptedVO rebuild(IndexRebuildRequest request) {
        if (!"REBUILD".equals(request.confirmText())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "全量重建确认文本必须为 REBUILD");
        }
        AiIndexTask existing = taskMapper.findByRequestId(request.clientRequestId());
        if (existing != null) {
            return new IndexTaskAcceptedVO(String.valueOf(existing.getId()));
        }
        if (taskMapper.findRunning() != null) {
            throw new BusinessException(ErrorCode.INDEX_TASK_CONFLICT, "已有索引任务正在执行");
        }
        AiIndexTask task = new AiIndexTask();
        task.setId(idGenerator.nextId());
        task.setTaskType("FULL_REBUILD");
        task.setTaskStatus("PENDING");
        task.setRequestId(request.clientRequestId());
        taskMapper.insert(task);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.rebuild(task.getId());
            }
        });
        return new IndexTaskAcceptedVO(String.valueOf(task.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public IndexTaskAcceptedVO reconcile(IndexReconcileRequest request) {
        AiIndexTask existing = taskMapper.findByRequestId(request.clientRequestId());
        if (existing != null) {
            return new IndexTaskAcceptedVO(String.valueOf(existing.getId()));
        }
        if (taskMapper.findRunning() != null) {
            throw new BusinessException(ErrorCode.INDEX_TASK_CONFLICT, "已有索引任务正在执行");
        }
        AiIndexTask task = new AiIndexTask();
        task.setId(idGenerator.nextId());
        task.setTaskType("RECONCILE");
        task.setTaskStatus("PENDING");
        task.setRequestId(request.clientRequestId());
        taskMapper.insert(task);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.reconcile(task.getId());
            }
        });
        return new IndexTaskAcceptedVO(String.valueOf(task.getId()));
    }
}
