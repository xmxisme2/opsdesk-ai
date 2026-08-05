package com.opsdesk.ai.conversation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 用户对 AI 回答提交的质量反馈。 */
public record FeedbackRequest(
        @NotNull Rating rating,
        ReasonCode reasonCode,
        @Size(max = 1000) String comment
) {
    /** 反馈方向，只允许点赞或点踩。 */
    public enum Rating { UP, DOWN }
    /** 负反馈原因，用于后续质量样本聚合。 */
    public enum ReasonCode { INCORRECT, NO_ANSWER, BAD_REFERENCE, OUTDATED, OTHER }
}
