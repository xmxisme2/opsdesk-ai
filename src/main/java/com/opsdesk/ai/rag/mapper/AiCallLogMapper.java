package com.opsdesk.ai.rag.mapper;

import com.opsdesk.ai.rag.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;

/** AI 调用审计日志数据访问 Mapper。 */
@Mapper
public interface AiCallLogMapper {
    int insert(AiCallLog callLog);
}
