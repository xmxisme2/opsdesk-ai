package com.opsdesk.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.config.ChatProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** DeepSeek OpenAI 兼容 Chat Completions 适配器，不记录请求正文或密钥。 */
@Component
public class DeepSeekChatGateway implements ChatGateway {
    private final ObjectMapper objectMapper;
    private final ChatProperties properties;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public DeepSeekChatGateway(ObjectMapper objectMapper, ChatProperties properties) { this.objectMapper = objectMapper; this.properties = properties; }
    @Override public String chat(String systemPrompt, String userQuestion) {
        if (!properties.isConfigured()) throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "Chat 模型密钥未配置");
        try {
            Map<String, Object> payload = Map.of("model", properties.getModel(), "temperature", properties.getTemperature(),
                    "max_tokens", properties.getMaxTokens(), "messages", List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", userQuestion)));
            String base = properties.getBaseUrl().endsWith("/") ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1) : properties.getBaseUrl();
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey()).header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            String answer = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (response.statusCode() / 100 != 2 || answer.isBlank()) throw new BusinessException(ErrorCode.AI_SERVICE_FAILED, "Chat 模型未返回有效回答");
            return answer;
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw new BusinessException(ErrorCode.AI_SERVICE_FAILED, "Chat 模型调用失败"); }
    }
}
