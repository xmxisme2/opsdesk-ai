package com.opsdesk.ai.health.controller;

import com.opsdesk.ai.common.response.ApiResponse;
import com.opsdesk.ai.health.service.AiHealthService;
import com.opsdesk.ai.health.vo.AiServiceHealthVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主应用调用的 AI 服务内部健康接口。
 */
@RestController
@RequestMapping("/internal/health")
public class InternalHealthController {

    private final AiHealthService healthService;

    public InternalHealthController(AiHealthService healthService) {
        this.healthService = healthService;
    }

    @PostMapping("/check")
    @PreAuthorize("hasRole('SERVICE')")
    public ApiResponse<AiServiceHealthVO> check() {
        return ApiResponse.success(healthService.check());
    }
}
