package com.opsdesk.ai.health.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

/** AI 模型或基础连接真实测试请求。 */
public record AiConnectionTestRequest(
        @NotBlank @Pattern(regexp = "CHAT|EMBEDDING|OPENSEARCH") String target
) {
}
