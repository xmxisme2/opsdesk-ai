package com.opsdesk.ai.knowledge.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.config.OpenSearchProperties;
import com.opsdesk.ai.config.EmbeddingProperties;
import com.opsdesk.ai.knowledge.chunk.KnowledgeChunk;
import com.opsdesk.ai.knowledge.client.KnowledgeSnapshot;
import org.springframework.stereotype.Repository;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenSearch 知识混合索引仓储。
 *
 * <p>向量维度通过真实模型探测确定，并只在新索引 Mapping 中固化，禁止原地变更。</p>
 */
@Repository
public class OpenSearchKnowledgeRepository {
    private static final String INDEX_VERSION = "HYBRID_V1";
    private final ObjectMapper objectMapper;
    private final OpenSearchProperties properties;
    private final EmbeddingProperties embeddingProperties;
    private final HttpClient httpClient;

    public OpenSearchKnowledgeRepository(ObjectMapper objectMapper, OpenSearchProperties properties,
                                         EmbeddingProperties embeddingProperties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
        this.httpClient = buildClient(properties.isTrustSelfSigned());
    }

    public void ensureIndexAndAliases() {
        HttpResponse<String> head = send("HEAD", "/" + properties.getIndexName(), null);
        if (head.statusCode() == 404) {
            createIndex(properties.getIndexName());
        } else {
            ensureSuccess(head, "检查知识全文索引失败");
        }
        ensureAliases();
    }

    /** 首次启用索引消费时创建与真实模型维度一致的向量索引。 */
    public void ensureVectorIndexAndAliases(int dimensions) {
        HttpResponse<String> head = send("HEAD", "/" + properties.getIndexName(), null);
        if (head.statusCode() == 404) {
            createVectorIndex(properties.getIndexName(), dimensions);
        } else {
            ensureSuccess(head, "检查知识向量索引失败");
        }
        ensureAliases();
    }

    private void ensureAliases() {
        List<String> readIndexes = aliasIndexes(properties.getReadAlias());
        List<String> writeIndexes = aliasIndexes(properties.getWriteAlias());
        String activeIndex = !writeIndexes.isEmpty() ? writeIndexes.get(0)
                : (!readIndexes.isEmpty() ? readIndexes.get(0) : properties.getIndexName());
        List<Map<String, Object>> actions = new ArrayList<>();
        if (readIndexes.isEmpty()) {
            actions.add(Map.of("add", Map.of("index", activeIndex, "alias", properties.getReadAlias())));
        }
        if (writeIndexes.isEmpty()) {
            actions.add(Map.of("add", Map.of("index", activeIndex, "alias", properties.getWriteAlias(),
                    "is_write_index", true)));
        }
        if (!actions.isEmpty()) {
            ensureSuccess(send("POST", "/_aliases", write(Map.of("actions", actions))),
                    "配置知识索引别名失败");
        }
    }

    /** 只探测集群可达性，不返回节点信息或认证配置。 */
    public boolean checkConnection() {
        try {
            return send("GET", "/", null).statusCode() / 100 == 2;
        } catch (Exception exception) {
            return false;
        }
    }

    /** 创建指定版本索引，不自动绑定别名。 */
    public void createIndex(String indexName) {
        HttpResponse<String> response = send("PUT", "/" + indexName, bm25Mapping());
        ensureSuccess(response, "创建知识全文索引失败");
    }

    /** 创建带 HNSW cosine 向量字段的新版本索引。 */
    public void createVectorIndex(String indexName, int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("向量维度必须大于零");
        }
        ensureSuccess(send("PUT", "/" + indexName, vectorMapping(dimensions)), "创建知识向量索引失败");
    }

    /** 仅供失败重建清理和本地集成测试使用，不暴露为外部接口。 */
    void deleteIndex(String indexName) {
        ensureSuccess(send("DELETE", "/" + indexName, null), "清理知识索引失败");
    }

    /** 将完整文章的全部分块写入指定索引，并清理该索引内的旧分块。 */
    public void indexChunks(KnowledgeSnapshot snapshot, List<KnowledgeChunk> chunks,
                            List<float[]> vectors, String targetIndex) {
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("知识分块与向量数量不一致");
        }
        List<String> tagIds = snapshot.tags() == null ? List.of()
                : snapshot.tags().stream().map(KnowledgeSnapshot.Tag::id).toList();
        List<String> tagNames = snapshot.tags() == null ? List.of()
                : snapshot.tags().stream().map(KnowledgeSnapshot.Tag::name).toList();
        List<String> currentChunkIds = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeChunk chunk = chunks.get(index);
            String chunkId = chunkId(snapshot, chunk);
            currentChunkIds.add(chunkId);
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("article_id", snapshot.articleId());
            document.put("article_version", snapshot.version());
            document.put("chunk_id", chunkId);
            document.put("chunk_no", chunk.chunkNo());
            document.put("title", snapshot.title());
            document.put("heading", chunk.heading());
            document.put("summary", snapshot.summary());
            document.put("content", chunk.content());
            document.put("embedding", vectors.get(index));
            document.put("embedding_provider", embeddingProperties.getProvider());
            document.put("embedding_model", embeddingProperties.getModel());
            document.put("category_id", snapshot.categoryId());
            document.put("category_name", snapshot.categoryName());
            document.put("tag_ids", tagIds);
            document.put("tags", tagNames);
            document.put("status", snapshot.status());
            document.put("visibility", snapshot.visibility());
            document.put("allowed_role_codes", safeList(snapshot.allowedRoleCodes()));
            document.put("allowed_department_ids", safeList(snapshot.allowedDepartmentIds()));
            document.put("source_ticket_id", snapshot.sourceTicketId());
            document.put("content_hash", chunk.contentHash());
            document.put("index_version", INDEX_VERSION);
            document.put("published_at", snapshot.publishedAt());
            document.put("updated_at", snapshot.updatedAt());
            ensureSuccess(send("PUT", "/" + targetIndex + "/_doc/" + chunkId, write(document)),
                    "写入知识向量索引失败");
        }
        Map<String, Object> cleanup = Map.of("query", Map.of("bool", Map.of(
                "filter", List.of(Map.of("term", Map.of("article_id", snapshot.articleId()))),
                "must_not", List.of(Map.of("terms", Map.of("chunk_id", currentChunkIds)))
        )));
        ensureSuccess(send("POST", "/" + targetIndex + "/_delete_by_query?conflicts=proceed",
                write(cleanup)), "清理文章旧版本索引失败");
    }

    /** 全量重建完成后原子切换读写别名，旧索引保留用于人工回滚。 */
    public void switchAliases(String targetIndex) {
        List<Map<String, Object>> actions = new ArrayList<>();
        aliasIndexes(properties.getReadAlias()).forEach(index ->
                actions.add(Map.of("remove", Map.of("index", index, "alias", properties.getReadAlias()))));
        aliasIndexes(properties.getWriteAlias()).forEach(index ->
                actions.add(Map.of("remove", Map.of("index", index, "alias", properties.getWriteAlias()))));
        actions.add(Map.of("add", Map.of("index", targetIndex, "alias", properties.getReadAlias())));
        actions.add(Map.of("add", Map.of("index", targetIndex, "alias", properties.getWriteAlias(),
                "is_write_index", true)));
        ensureSuccess(send("POST", "/_aliases", write(Map.of("actions", actions))),
                "切换知识索引别名失败");
    }

    public void remove(String articleId) {
        ensureIndexAndAliases();
        Map<String, Object> query = Map.of("query", Map.of("term", Map.of("article_id", articleId)));
        ensureSuccess(send("POST", "/" + properties.getWriteAlias() + "/_delete_by_query?conflicts=proceed",
                write(query)), "移除知识全文索引失败");
    }

    public List<KnowledgeSearchHit> search(String keyword, int size) {
        return searchKeyword(keyword, size);
    }

    /** BM25 关键词候选召回。 */
    public List<KnowledgeSearchHit> searchKeyword(String keyword, int size) {
        ensureIndexAndAliases();
        Map<String, Object> body = Map.of(
                "size", Math.min(Math.max(size, 1), 100),
                "query", Map.of("bool", Map.of(
                        "must", List.of(Map.of("multi_match", Map.of(
                                "query", keyword,
                                "fields", List.of("title^4", "heading^3", "summary^2", "content", "tags^2")
                        ))),
                        "filter", List.of(Map.of("term", Map.of("status", "PUBLISHED")))
                ))
        );
        HttpResponse<String> response = send("POST", "/" + properties.getReadAlias() + "/_search", write(body));
        ensureSuccess(response, "知识全文检索失败");
        return parseHits(response, true);
    }

    /** OpenSearch 原生 k-NN 向量候选召回。 */
    public List<KnowledgeSearchHit> searchVector(float[] queryVector, int size) {
        ensureIndexAndAliases();
        Map<String, Object> body = Map.of(
                "size", Math.min(Math.max(size, 1), 100),
                "query", Map.of("knn", Map.of("embedding", Map.of(
                        "vector", queryVector,
                        "k", Math.min(Math.max(size, 1), 100),
                        "filter", Map.of("term", Map.of("status", "PUBLISHED"))
                )))
        );
        HttpResponse<String> response = send("POST", "/" + properties.getReadAlias() + "/_search", write(body));
        ensureSuccess(response, "知识向量检索失败");
        return parseHits(response, false);
    }

    public String writeAlias() {
        return properties.getWriteAlias();
    }

    private List<KnowledgeSearchHit> parseHits(HttpResponse<String> response, boolean keyword) {
        try {
            List<KnowledgeSearchHit> hits = new ArrayList<>();
            for (JsonNode hit : objectMapper.readTree(response.body()).path("hits").path("hits")) {
                JsonNode source = hit.path("_source");
                double score = hit.path("_score").asDouble();
                hits.add(new KnowledgeSearchHit(
                        source.path("article_id").asText(), source.path("article_version").asLong(),
                        source.path("chunk_id").asText(), source.path("title").asText(),
                        source.path("heading").asText(), source.path("content").asText(),
                        keyword ? score : 0, keyword ? 0 : score, 0
                ));
            }
            return hits;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "知识检索响应解析失败");
        }
    }

    private String bm25Mapping() {
        return mappingProperties(false, 0);
    }

    private String vectorMapping(int dimensions) {
        return mappingProperties(true, dimensions);
    }

    private String mappingProperties(boolean vectorEnabled, int dimensions) {
        String vectorSettings = vectorEnabled ? "\"knn\": true," : "";
        String vectorField = vectorEnabled ? """
                      "embedding": {"type": "knn_vector", "dimension": %d,
                        "method": {"name": "hnsw", "engine": "lucene", "space_type": "cosinesimil",
                          "parameters": {"ef_construction": 128, "m": 16}}},
                      "embedding_provider": {"type": "keyword"},
                      "embedding_model": {"type": "keyword"},
                """.formatted(dimensions) : "";
        return """
                {
                  "settings": {
                    "index": {%s "number_of_shards": 1, "number_of_replicas": 0},
                    "analysis": {"analyzer": {"opsdesk_cjk": {
                      "type": "custom", "tokenizer": "standard", "filter": ["lowercase", "cjk_bigram"]
                    }}}
                  },
                  "mappings": {
                    "dynamic": "strict",
                    "properties": {
                      "article_id": {"type": "keyword"},
                      "article_version": {"type": "long"},
                      "chunk_id": {"type": "keyword"},
                      "chunk_no": {"type": "integer"},
                      "title": {"type": "text", "analyzer": "opsdesk_cjk",
                        "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}},
                      "heading": {"type": "text", "analyzer": "opsdesk_cjk"},
                      "summary": {"type": "text", "analyzer": "opsdesk_cjk"},
                      "content": {"type": "text", "analyzer": "opsdesk_cjk"},
                %s
                      "category_id": {"type": "keyword"},
                      "category_name": {"type": "keyword"},
                      "tag_ids": {"type": "keyword"},
                      "tags": {"type": "keyword"},
                      "status": {"type": "keyword"},
                      "visibility": {"type": "keyword"},
                      "allowed_role_codes": {"type": "keyword"},
                      "allowed_department_ids": {"type": "keyword"},
                      "source_ticket_id": {"type": "keyword"},
                      "content_hash": {"type": "keyword"},
                      "index_version": {"type": "keyword"},
                      "published_at": {"type": "date"},
                      "updated_at": {"type": "date"}
                    }
                  }
                }
                """.formatted(vectorSettings, vectorField);
    }

    private List<String> aliasIndexes(String alias) {
        HttpResponse<String> response = send("GET", "/opsdesk_knowledge_*/_alias/" + alias, null);
        if (response.statusCode() == 404) {
            return List.of();
        }
        ensureSuccess(response, "读取知识索引别名失败");
        try {
            List<String> indexes = new ArrayList<>();
            objectMapper.readTree(response.body()).fieldNames().forEachRemaining(indexes::add);
            return indexes;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "知识索引别名响应解析失败");
        }
    }

    private String chunkId(KnowledgeSnapshot snapshot, KnowledgeChunk chunk) {
        String source = snapshot.articleId() + ":" + snapshot.version() + ":" + chunk.chunkNo()
                + ":" + chunk.contentHash();
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
            return snapshot.articleId() + ":" + snapshot.version() + ":" + chunk.chunkNo()
                    + ":" + hash.substring(0, 12);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "索引分块 ID 生成失败");
        }
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private HttpResponse<String> send(String method, String path, String body) {
        try {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .method(method, publisher);
            if (body != null) {
                builder.header("Content-Type", "application/json");
            }
            if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
                String auth = properties.getUsername() + ":" + properties.getPassword();
                builder.header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "OpenSearch 连接失败");
        }
    }

    private void ensureSuccess(HttpResponse<String> response, String message) {
        if (response.statusCode() / 100 != 2) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                    message + "（HTTP " + response.statusCode() + "）");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OpenSearch 请求序列化失败");
        }
    }

    private String baseUrl() {
        String value = properties.getUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private HttpClient buildClient(boolean trustSelfSigned) {
        try {
            HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5));
            if (trustSelfSigned) {
                TrustManager[] trustManagers = {new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                    public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                }};
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, trustManagers, new SecureRandom());
                SSLParameters parameters = new SSLParameters();
                parameters.setEndpointIdentificationAlgorithm("");
                builder.sslContext(context).sslParameters(parameters);
            }
            return builder.build();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OpenSearch HTTP 客户端初始化失败");
        }
    }
}
