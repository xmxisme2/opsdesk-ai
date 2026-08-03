package com.opsdesk.ai.rag.controller;

import com.opsdesk.ai.common.response.ApiResponse;
import com.opsdesk.ai.rag.dto.RagChatRequest;
import com.opsdesk.ai.rag.service.KnowledgeRagService;
import com.opsdesk.ai.rag.service.KnowledgeRagStreamService;
import com.opsdesk.ai.rag.vo.RagChatResponse;
import com.opsdesk.ai.security.ServicePrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletRequest;

/** 仅供主应用代理调用的单轮 RAG JSON 接口。 */
@RestController
@RequestMapping("/internal/rag/knowledge")
public class InternalKnowledgeRagController {
    private final KnowledgeRagService service;
    private final KnowledgeRagStreamService streamService;
    public InternalKnowledgeRagController(KnowledgeRagService service, KnowledgeRagStreamService streamService) {
        this.service = service; this.streamService = streamService;
    }
    @PostMapping("/chat")
    public ApiResponse<RagChatResponse> chat(@AuthenticationPrincipal ServicePrincipal principal, @Valid @RequestBody RagChatRequest request) {
        return ApiResponse.success(service.chat(principal, request.question()));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal ServicePrincipal principal,
                             @Valid @RequestBody RagChatRequest request, HttpServletRequest servletRequest) {
        return streamService.stream(principal, request.question(), request.clientRequestId(), servletRequest.getHeader("X-Trace-Id"));
    }
}
