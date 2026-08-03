package com.opsdesk.ai.knowledge.client;

import java.time.LocalDateTime;
import java.util.List;

/** 主应用返回的知识文章索引快照。 */
public record KnowledgeSnapshot(
        String articleId,
        long version,
        String title,
        String summary,
        String content,
        String categoryId,
        String categoryName,
        List<Tag> tags,
        String sourceTicketId,
        String status,
        String visibility,
        List<String> allowedRoleCodes,
        List<String> allowedDepartmentIds,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt,
        String contentHash
) {
    /** 标签最小快照。 */
    public record Tag(String id, String name) {
    }
}
