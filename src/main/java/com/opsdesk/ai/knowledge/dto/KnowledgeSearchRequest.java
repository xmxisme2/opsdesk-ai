package com.opsdesk.ai.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** AI 内部混合检索请求。 */
public record KnowledgeSearchRequest(
        @NotBlank String keyword,
        @Positive @Max(20) Integer size
) {
}
