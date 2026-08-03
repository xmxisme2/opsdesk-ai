package com.opsdesk.ai.common.id;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 服务本地递增雪花风格 ID 生成器。
 *
 * <p>阶段 2 单实例开发环境使用；生产横向扩容前必须替换为带机器位的统一实现。</p>
 */
@Component
public class LocalSnowflakeIdGenerator {
    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis() << 20);

    public long nextId() {
        return sequence.incrementAndGet();
    }
}
