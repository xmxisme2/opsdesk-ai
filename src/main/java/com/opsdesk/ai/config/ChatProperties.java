package com.opsdesk.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** DeepSeek Chat 连接配置；密钥只允许来自环境变量或已忽略的本地属性文件。 */
@ConfigurationProperties(prefix = "opsdesk.ai.chat")
public class ChatProperties {
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-chat";
    private double temperature = 0.1D;
    private int maxTokens = 4096;
    private int timeoutSeconds = 120;
    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
