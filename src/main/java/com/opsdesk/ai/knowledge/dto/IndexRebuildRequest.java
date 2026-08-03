package com.opsdesk.ai.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

/** 全量索引重建确认请求。 */
public record IndexRebuildRequest(@NotBlank String confirmText, @NotBlank String clientRequestId) {
}
