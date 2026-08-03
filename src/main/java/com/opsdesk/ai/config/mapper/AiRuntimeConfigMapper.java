package com.opsdesk.ai.config.mapper;

import com.opsdesk.ai.config.entity.AiRuntimeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI 运行配置 Mapper。
 *
 * <p>查询 SQL 统一维护在 XML 中，禁止在接口上使用 SQL 注解。</p>
 */
@Mapper
public interface AiRuntimeConfigMapper {

    AiRuntimeConfig selectActiveByKey(@Param("configKey") String configKey);
}
