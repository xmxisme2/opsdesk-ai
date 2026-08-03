package com.opsdesk.ai.knowledge.controller;

import com.opsdesk.ai.common.response.ApiResponse;
import com.opsdesk.ai.knowledge.dto.IndexRebuildRequest;
import com.opsdesk.ai.knowledge.dto.IndexReconcileRequest;
import com.opsdesk.ai.knowledge.service.IndexAdminService;
import com.opsdesk.ai.knowledge.vo.IndexTaskAcceptedVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 主应用调用的内部索引管理接口。 */
@RestController
@RequestMapping("/internal/admin/index")
public class InternalIndexAdminController {
    private final IndexAdminService service;

    public InternalIndexAdminController(IndexAdminService service) {
        this.service = service;
    }

    @PostMapping("/rebuild")
    public ApiResponse<IndexTaskAcceptedVO> rebuild(@Valid @RequestBody IndexRebuildRequest request) {
        return ApiResponse.success(service.rebuild(request));
    }

    @PostMapping("/reconcile")
    public ApiResponse<IndexTaskAcceptedVO> reconcile(@Valid @RequestBody IndexReconcileRequest request) {
        return ApiResponse.success(service.reconcile(request));
    }
}
