package com.opsdesk.ai.health.controller;

import com.opsdesk.ai.common.response.ApiResponse;
import com.opsdesk.ai.embedding.EmbeddingGateway;
import com.opsdesk.ai.embedding.EmbeddingHealth;
import com.opsdesk.ai.health.dto.AiConnectionTestRequest;
import com.opsdesk.ai.health.vo.AiConnectionTestVO;
import com.opsdesk.ai.knowledge.search.OpenSearchKnowledgeRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/** 主应用调用的模型与连接真实测试入口。 */
@RestController
@RequestMapping("/internal/admin/model")
public class InternalConnectionTestController {
    private final EmbeddingGateway embeddingGateway;
    private final OpenSearchKnowledgeRepository openSearchRepository;

    public InternalConnectionTestController(EmbeddingGateway embeddingGateway,
                                            OpenSearchKnowledgeRepository openSearchRepository) {
        this.embeddingGateway = embeddingGateway;
        this.openSearchRepository = openSearchRepository;
    }

    @PostMapping("/test")
    public ApiResponse<AiConnectionTestVO> test(@Valid @RequestBody AiConnectionTestRequest request) {
        if ("EMBEDDING".equals(request.target())) {
            EmbeddingHealth health = embeddingGateway.checkHealth();
            return ApiResponse.success(new AiConnectionTestVO(request.target(), health.configured(),
                    health.success(), health.provider(), health.model(), health.dimensions(),
                    health.latencyMs(), health.message()));
        }
        if ("OPENSEARCH".equals(request.target())) {
            Instant start = Instant.now();
            boolean success = openSearchRepository.checkConnection();
            return ApiResponse.success(new AiConnectionTestVO(request.target(), true, success,
                    "opensearch", null, null, Duration.between(start, Instant.now()).toMillis(),
                    success ? "连接成功" : "连接失败"));
        }
        return ApiResponse.success(new AiConnectionTestVO(request.target(), false, false,
                "deepseek", null, null, 0, "Chat 模型将在生成阶段接入"));
    }
}
