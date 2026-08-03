package com.opsdesk.ai.knowledge.mapper;

import com.opsdesk.ai.knowledge.entity.KnowledgeIndexState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 知识索引状态数据访问 Mapper。 */
@Mapper
public interface KnowledgeIndexStateMapper {
    KnowledgeIndexState findByArticleId(@Param("articleId") Long articleId);
    List<KnowledgeIndexState> findAll();
    int insert(KnowledgeIndexState state);
    int update(KnowledgeIndexState state);
}
