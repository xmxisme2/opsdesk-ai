package com.opsdesk.ai.knowledge.mapper;

import com.opsdesk.ai.knowledge.entity.EventConsumptionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 事件消费日志数据访问 Mapper。 */
@Mapper
public interface EventConsumptionLogMapper {
    EventConsumptionLog findByEventId(@Param("eventId") String eventId);
    int insert(EventConsumptionLog log);
    int markProcessing(@Param("eventId") String eventId);
    int markSuccess(@Param("eventId") String eventId);
}
