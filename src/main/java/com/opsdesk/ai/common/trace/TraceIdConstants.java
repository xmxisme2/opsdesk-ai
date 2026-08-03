package com.opsdesk.ai.common.trace;

/**
 * 跨服务 TraceId 常量。
 */
public final class TraceIdConstants {

    /** HTTP TraceId 请求与响应头，不允许业务接口覆盖名称。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    /** 日志 MDC 中的 TraceId 键。 */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private TraceIdConstants() {
    }
}
