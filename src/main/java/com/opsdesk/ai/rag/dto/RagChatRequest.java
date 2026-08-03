package com.opsdesk.ai.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 主应用代理单轮知识问答时传入的已鉴权用户问题。 */
public record RagChatRequest(
        @NotBlank @Size(max = 2000) String question,
        @Size(max = 64) String clientRequestId
) { }
