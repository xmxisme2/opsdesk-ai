package com.opsdesk.ai.config.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 非敏感运行配置实体。
 *
 * <p>模型密钥、数据库密码和中间件密码禁止写入该表。</p>
 */
@Getter
@Setter
public class AiRuntimeConfig {

    private Long id;
    private String configKey;
    private String configValue;
    private String valueType;
    private String configGroup;
    private String description;
    private Boolean editable;
    private Long configVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Boolean deleted;
}
