package com.opsdesk.ai.conversation.vo;

import java.util.List;

/** AI 服务内部使用的统一分页响应。 */
public record PageResult<T>(List<T> records, long page, long size, long total) { }
