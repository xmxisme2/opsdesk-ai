package com.opsdesk.ai.knowledge.mapper;

import com.opsdesk.ai.knowledge.entity.AiIndexTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 索引任务数据访问 Mapper。 */
@Mapper
public interface AiIndexTaskMapper {
    AiIndexTask findByRequestId(@Param("requestId") String requestId);
    AiIndexTask findRunning();
    int insert(AiIndexTask task);
    int markRunning(@Param("id") Long id, @Param("targetIndexName") String targetIndexName);
    int markSuccess(@Param("id") Long id, @Param("totalCount") int totalCount,
                    @Param("successCount") int successCount);
    int markFailed(@Param("id") Long id, @Param("totalCount") int totalCount,
                   @Param("successCount") int successCount, @Param("lastError") String lastError);
}
