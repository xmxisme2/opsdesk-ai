package com.opsdesk.ai.knowledge.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.config.AiIntegrationProperties;
import com.opsdesk.ai.security.OutboundServiceJwtTokenIssuer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.List;

/**
 * 受控读取主应用知识快照的 HTTP 客户端。
 *
 * <p>客户端只访问 /internal 接口，不允许直连 OpsDesk 业务数据库。</p>
 */
@Component
public class KnowledgeSnapshotClient {
    private final ObjectMapper objectMapper;
    private final AiIntegrationProperties properties;
    private final OutboundServiceJwtTokenIssuer tokenIssuer;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public KnowledgeSnapshotClient(ObjectMapper objectMapper,
                                   AiIntegrationProperties properties,
                                   OutboundServiceJwtTokenIssuer tokenIssuer) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tokenIssuer = tokenIssuer;
    }

    public KnowledgeSnapshot fetch(String articleId, long expectedVersion, String eventId, String traceId) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "expectedVersion", expectedVersion,
                    "eventId", eventId
            ));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl() + "/internal/knowledge/articles/"
                            + articleId + "/index-snapshot"))
                    .timeout(Duration.ofSeconds(10))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenIssuer.issue())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (traceId != null && !traceId.isBlank()) {
                builder.header("X-Trace-Id", traceId);
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = objectMapper.readTree(response.body());
            if (response.statusCode() / 100 != 2 || envelope.path("code").asInt() != 200) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                        "知识快照读取失败：" + envelope.path("message").asText("主应用响应异常"));
            }
            return objectMapper.treeToValue(envelope.get("data"), KnowledgeSnapshot.class);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "知识快照读取失败");
        }
    }

    public SnapshotPage fetchPublishedPage(String afterId, int limit, String traceId) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("afterId", afterId, "limit", limit));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl()
                            + "/internal/knowledge/articles/published-index-snapshots"))
                    .timeout(Duration.ofSeconds(30))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenIssuer.issue())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (traceId != null && !traceId.isBlank()) {
                builder.header("X-Trace-Id", traceId);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = objectMapper.readTree(response.body());
            if (response.statusCode() / 100 != 2 || envelope.path("code").asInt() != 200) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "已发布知识快照分页读取失败");
            }
            return objectMapper.treeToValue(envelope.get("data"), SnapshotPage.class);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "已发布知识快照分页读取失败");
        }
    }

    /** 在返回引用前向主应用复核文章仍为已发布且当前用户可访问。 */
    public List<String> checkArticleAccess(String userId, List<String> articleIds, String traceId) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "userId", userId, "articleIds", articleIds, "requiredStatus", "PUBLISHED"));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl() + "/internal/knowledge/articles/access-check"))
                    .timeout(Duration.ofSeconds(10))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenIssuer.issue())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (traceId != null && !traceId.isBlank()) builder.header("X-Trace-Id", traceId);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = objectMapper.readTree(response.body());
            if (response.statusCode() / 100 != 2 || envelope.path("code").asInt() != 200) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "引用权限复核失败");
            }
            return objectMapper.convertValue(envelope.path("data").path("accessibleArticleIds"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "引用权限复核失败"); }
    }

    /** 全量重建使用的游标快照页。 */
    public record SnapshotPage(List<KnowledgeSnapshot> items, String nextAfterId, boolean hasMore) {
    }

    private String normalizeBaseUrl() {
        String value = properties.getOpsdeskBaseUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
