package com.opsdesk.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 索引链路与主应用连接配置。 */
@Component
@ConfigurationProperties(prefix = "opsdesk.ai")
public class AiIntegrationProperties {
    private boolean indexingEnabled;
    private String opsdeskBaseUrl = "http://127.0.0.1:8080";

    public boolean isIndexingEnabled() {
        return indexingEnabled;
    }

    public void setIndexingEnabled(boolean indexingEnabled) {
        this.indexingEnabled = indexingEnabled;
    }

    public String getOpsdeskBaseUrl() {
        return opsdeskBaseUrl;
    }

    public void setOpsdeskBaseUrl(String opsdeskBaseUrl) {
        this.opsdeskBaseUrl = opsdeskBaseUrl;
    }
}
