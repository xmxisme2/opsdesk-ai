package com.opsdesk.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** OpenSearch 全文索引连接与别名配置。 */
@Component
@ConfigurationProperties(prefix = "opsdesk.ai.opensearch")
public class OpenSearchProperties {
    private String url = "https://127.0.0.1:9200";
    private String username = "opsdesk_ai";
    private String password = "";
    private boolean trustSelfSigned = true;
    private String indexName = "opsdesk_knowledge_v001";
    private String readAlias = "opsdesk_knowledge_read";
    private String writeAlias = "opsdesk_knowledge_write";
    private int hybridCandidateSize = 20;
    private int maxChunksPerArticle = 2;
    private double hybridMinScore = 0.35;
    private double keywordWeight = 0.45;
    private double vectorWeight = 0.55;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isTrustSelfSigned() { return trustSelfSigned; }
    public void setTrustSelfSigned(boolean trustSelfSigned) { this.trustSelfSigned = trustSelfSigned; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public String getReadAlias() { return readAlias; }
    public void setReadAlias(String readAlias) { this.readAlias = readAlias; }
    public String getWriteAlias() { return writeAlias; }
    public void setWriteAlias(String writeAlias) { this.writeAlias = writeAlias; }
    public int getHybridCandidateSize() { return hybridCandidateSize; }
    public void setHybridCandidateSize(int hybridCandidateSize) { this.hybridCandidateSize = hybridCandidateSize; }
    public int getMaxChunksPerArticle() { return maxChunksPerArticle; }
    public void setMaxChunksPerArticle(int maxChunksPerArticle) { this.maxChunksPerArticle = maxChunksPerArticle; }
    public double getHybridMinScore() { return hybridMinScore; }
    public void setHybridMinScore(double hybridMinScore) { this.hybridMinScore = hybridMinScore; }
    public double getKeywordWeight() { return keywordWeight; }
    public void setKeywordWeight(double keywordWeight) { this.keywordWeight = keywordWeight; }
    public double getVectorWeight() { return vectorWeight; }
    public void setVectorWeight(double vectorWeight) { this.vectorWeight = vectorWeight; }
}
