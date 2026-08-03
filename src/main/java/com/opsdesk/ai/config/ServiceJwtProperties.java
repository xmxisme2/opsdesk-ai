package com.opsdesk.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务间 JWT 校验配置。
 *
 * <p>共享密钥只允许来自部署环境；密钥缺失时受保护内部接口必须安全失败。</p>
 */
@ConfigurationProperties(prefix = "opsdesk.ai.service-jwt")
public class ServiceJwtProperties {

    private String secret = "";
    private String issuer = "opsdesk-backend";
    private String audience = "opsdesk-ai-service";
    private long maxClockSkewSeconds = 30L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public long getMaxClockSkewSeconds() {
        return maxClockSkewSeconds;
    }

    public void setMaxClockSkewSeconds(long maxClockSkewSeconds) {
        this.maxClockSkewSeconds = maxClockSkewSeconds;
    }
}
