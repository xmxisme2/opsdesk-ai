package com.opsdesk.ai.conversation.controller;

import com.opsdesk.ai.common.response.ApiResponse;
import com.opsdesk.ai.conversation.dto.ConversationSearchRequest;
import com.opsdesk.ai.conversation.dto.FeedbackRequest;
import com.opsdesk.ai.conversation.service.AiConversationService;
import com.opsdesk.ai.conversation.vo.ConversationActionVO;
import com.opsdesk.ai.conversation.vo.ConversationDetailVO;
import com.opsdesk.ai.conversation.vo.ConversationVO;
import com.opsdesk.ai.conversation.vo.PageResult;
import com.opsdesk.ai.security.ServicePrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 主应用专用 AI 会话接口，所有操作均以 Service JWT 中的用户 ID 做资源隔离。 */
@RestController
@RequestMapping("/internal/ai")
public class InternalAiConversationController {
    private final AiConversationService service;
    public InternalAiConversationController(AiConversationService service) { this.service = service; }

    @PostMapping("/conversations/search")
    public ApiResponse<PageResult<ConversationVO>> search(@AuthenticationPrincipal ServicePrincipal principal,
                                                           @Valid @RequestBody ConversationSearchRequest request) {
        return ApiResponse.success(service.search(principal, request));
    }

    @PostMapping("/conversations/{id}/detail")
    public ApiResponse<ConversationDetailVO> detail(@AuthenticationPrincipal ServicePrincipal principal,
                                                     @PathVariable String id) {
        return ApiResponse.success(service.detail(principal, id));
    }

    @PostMapping("/conversations/{id}/archive")
    public ApiResponse<ConversationActionVO> archive(@AuthenticationPrincipal ServicePrincipal principal,
                                                      @PathVariable String id) {
        return ApiResponse.success(service.archive(principal, id));
    }

    @PostMapping("/conversations/{id}/delete")
    public ApiResponse<ConversationActionVO> delete(@AuthenticationPrincipal ServicePrincipal principal,
                                                     @PathVariable String id) {
        return ApiResponse.success(service.delete(principal, id));
    }

    @PostMapping("/messages/{id}/feedback")
    public ApiResponse<Void> feedback(@AuthenticationPrincipal ServicePrincipal principal, @PathVariable String id,
                                      @Valid @RequestBody FeedbackRequest request) {
        service.feedback(principal, id, request);
        return ApiResponse.success(null);
    }
}
