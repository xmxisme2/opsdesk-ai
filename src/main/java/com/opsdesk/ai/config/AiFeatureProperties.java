package com.opsdesk.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 功能开关配置。
 *
 * <p>阶段 1 的两个开关都必须保持关闭，后续完成安全与质量验收后才能启用。</p>
 */
@ConfigurationProperties(prefix = "opsdesk.ai")
public class AiFeatureProperties {

    private boolean enabled;
    private boolean ragEnabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRagEnabled() {
        return ragEnabled;
    }

    public void setRagEnabled(boolean ragEnabled) {
        this.ragEnabled = ragEnabled;
    }
}
