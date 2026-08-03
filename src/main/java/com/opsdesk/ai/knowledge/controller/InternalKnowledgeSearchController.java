package com.opsdesk.ai.knowledge.controller;

import com.opsdesk.ai.common.response.ApiResponse;
import com.opsdesk.ai.knowledge.dto.KnowledgeSearchRequest;
import com.opsdesk.ai.knowledge.search.KnowledgeSearchHit;
import com.opsdesk.ai.knowledge.service.HybridKnowledgeSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 主应用调用的内部 BM25 与向量混合检索入口。 */
@RestController
@RequestMapping("/internal/knowledge")
public class InternalKnowledgeSearchController {
    private final HybridKnowledgeSearchService searchService;

    public InternalKnowledgeSearchController(HybridKnowledgeSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public ApiResponse<List<KnowledgeSearchHit>> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        return ApiResponse.success(searchService.search(
                request.keyword().trim(), request.size() == null ? 6 : request.size()));
    }
}
