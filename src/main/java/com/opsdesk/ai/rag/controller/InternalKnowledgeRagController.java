package com.opsdesk.ai.rag.controller;

import com.opsdesk.ai.common.response.ApiResponse;
import com.opsdesk.ai.rag.dto.RagChatRequest;
import com.opsdesk.ai.rag.service.KnowledgeRagService;
import com.opsdesk.ai.rag.vo.RagChatResponse;
import com.opsdesk.ai.security.ServicePrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅供主应用代理调用的单轮 RAG JSON 接口。 */
@RestController
@RequestMapping("/internal/rag/knowledge")
public class InternalKnowledgeRagController {
    private final KnowledgeRagService service;
    public InternalKnowledgeRagController(KnowledgeRagService service) { this.service = service; }
    @PostMapping("/chat")
    public ApiResponse<RagChatResponse> chat(@AuthenticationPrincipal ServicePrincipal principal, @Valid @RequestBody RagChatRequest request) {
        return ApiResponse.success(service.chat(principal, request.question()));
    }
}
