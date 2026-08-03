package com.opsdesk.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 腾讯云知识引擎 Embedding 模型连接配置。 */
@Component
@ConfigurationProperties(prefix = "opsdesk.ai.embedding")
public class EmbeddingProperties {
    private String provider = "tencent-lkeap";
    private String endpoint = "https://lkeap.tencentcloudapi.com";
    private String model = "lke-text-embedding-v2";
    private String region = "ap-guangzhou";
    private String secretId = "";
    private String secretKey = "";
    private int timeoutSeconds = 30;
    private int batchSize = 7;

    /** 只有两个密钥字段同时存在时才允许发起外部模型请求。 */
    public boolean isConfigured() {
        return secretId != null && !secretId.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getSecretId() { return secretId; }
    public void setSecretId(String secretId) { this.secretId = secretId; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
}
