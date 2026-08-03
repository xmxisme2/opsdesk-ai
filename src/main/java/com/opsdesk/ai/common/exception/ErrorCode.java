package com.opsdesk.ai.common.exception;

/**
 * AI 服务统一错误码，必须与主应用 API 契约保持一致。
 */
public enum ErrorCode {

    /** 参数错误：请求字段缺失、格式错误或取值非法时使用。 */
    PARAM_ERROR(400001, "请求参数错误"),
    /** 未认证：Service JWT 缺失、过期或签名无效时使用。 */
    UNAUTHORIZED(401001, "未登录"),
    /** 无权限：服务身份或用户上下文不满足接口要求时使用。 */
    FORBIDDEN(403001, "无权限"),
    /** 系统异常：未预期异常或基础设施故障时使用。 */
    SYSTEM_ERROR(500001, "系统异常"),
    /** AI 模型调用失败：外部 Chat 服务超时、拒绝或响应不完整时使用。 */
    AI_SERVICE_FAILED(500201, "AI 服务调用失败"),
    /** AI 服务不可用：关键凭据或依赖未准备完成时使用。 */
    AI_SERVICE_UNAVAILABLE(500202, "AI 服务不可用"),
    /** 知识检索失败：OpenSearch 查询或结果融合失败时使用。 */
    KNOWLEDGE_SEARCH_FAILED(500203, "知识检索失败"),
    /** 向量生成失败：Embedding 提供方调用失败时使用。 */
    EMBEDDING_FAILED(500204, "向量生成失败"),
    /** 索引任务冲突：已有全量任务处于待执行或执行中。 */
    INDEX_TASK_CONFLICT(409201, "索引任务已在执行"),
    /** AI 请求参数错误：问题为空、过长或格式不符合问答约束时使用。 */
    AI_REQUEST_INVALID(400201, "AI 问题为空、过长或包含不允许内容");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
