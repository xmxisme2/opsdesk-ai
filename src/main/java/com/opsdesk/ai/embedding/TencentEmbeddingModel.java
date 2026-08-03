package com.opsdesk.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.config.EmbeddingProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 腾讯云知识引擎 GetEmbedding 的 Spring AI 模型适配器。
 *
 * <p>请求和响应严禁记录日志，避免文章正文、查询文本和向量泄露。</p>
 */
@Component
public class TencentEmbeddingModel implements EmbeddingModel {
    private static final String SERVICE = "lkeap";
    private static final String ACTION = "GetEmbedding";
    private static final String VERSION = "2024-05-22";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String SIGNED_HEADERS = "content-type;host;x-tc-action";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final EmbeddingProperties properties;
    private final HttpClient httpClient;

    public TencentEmbeddingModel(ObjectMapper objectMapper, EmbeddingProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(properties.getTimeoutSeconds(), 1)))
                .build();
    }

    /** Spring AI 标准入口默认按文档向量处理。 */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<float[]> vectors = requestEmbeddings(request.getInstructions(), "document");
        List<Embedding> results = new ArrayList<>(vectors.size());
        for (int index = 0; index < vectors.size(); index++) {
            results.add(new Embedding(vectors.get(index), index));
        }
        return new EmbeddingResponse(results, new EmbeddingResponseMetadata(properties.getModel(), null));
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    /** 查询向量需使用腾讯模型的 query 文本类型。 */
    public float[] embedQuery(String text) {
        return requestEmbeddings(List.of(text), "query").get(0);
    }

    /** 文档向量按供应商上限分批，避免单次输入数量超限。 */
    public List<float[]> embedDocuments(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        int batchSize = Math.min(Math.max(properties.getBatchSize(), 1), 7);
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            vectors.addAll(requestEmbeddings(texts.subList(start, Math.min(start + batchSize, texts.size())),
                    "document"));
        }
        return vectors;
    }

    private List<float[]> requestEmbeddings(List<String> texts, String textType) {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "Embedding 模型密钥未配置");
        }
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Embedding 输入不能为空");
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "Model", properties.getModel(),
                    "TextType", textType,
                    "Inputs", texts
            ));
            long timestamp = Instant.now().getEpochSecond();
            String authorization = authorization(payload, timestamp);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getEndpoint()))
                    .timeout(Duration.ofSeconds(Math.max(properties.getTimeoutSeconds(), 1)))
                    .header("Content-Type", CONTENT_TYPE)
                    .header("X-TC-Action", ACTION)
                    .header("X-TC-Version", VERSION)
                    .header("X-TC-Timestamp", String.valueOf(timestamp))
                    .header("X-TC-Region", properties.getRegion())
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.EMBEDDING_FAILED,
                    "Embedding 模型调用失败：" + exception.getClass().getSimpleName());
        }
    }

    private List<float[]> parseResponse(HttpResponse<String> response) {
        try {
            JsonNode root = objectMapper.readTree(response.body()).path("Response");
            JsonNode error = root.path("Error");
            if (response.statusCode() / 100 != 2 || !error.isMissingNode()) {
                String code = error.path("Code").asText("HTTP_" + response.statusCode());
                throw new BusinessException(ErrorCode.EMBEDDING_FAILED,
                        "Embedding 模型返回错误：" + code);
            }
            JsonNode data = root.path("Data");
            if (!data.isArray() || data.isEmpty()) {
                throw new BusinessException(ErrorCode.EMBEDDING_FAILED, "Embedding 模型未返回向量");
            }
            List<float[]> vectors = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode values = item.path("Embedding");
                float[] vector = new float[values.size()];
                for (int index = 0; index < values.size(); index++) {
                    vector[index] = (float) values.get(index).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.EMBEDDING_FAILED, "Embedding 模型响应解析失败");
        }
    }

    private String authorization(String payload, long timestamp) throws Exception {
        URI endpoint = URI.create(properties.getEndpoint());
        String canonicalHeaders = "content-type:" + CONTENT_TYPE + "\n"
                + "host:" + endpoint.getHost() + "\n"
                + "x-tc-action:" + ACTION.toLowerCase() + "\n";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n"
                + SIGNED_HEADERS + "\n" + sha256Hex(payload);
        String date = DATE_FORMATTER.format(Instant.ofEpochSecond(timestamp));
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
        byte[] secretDate = hmacSha256(("TC3" + properties.getSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));
        return ALGORITHM + " Credential=" + properties.getSecretId() + "/" + credentialScope
                + ", SignedHeaders=" + SIGNED_HEADERS + ", Signature=" + signature;
    }

    private byte[] hmacSha256(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
