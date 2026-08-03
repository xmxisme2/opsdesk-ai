package com.opsdesk.ai.rag.vo;

/** 已经主应用权限复核的回答引用快照。 */
public record RagReferenceVO(String articleId, String title, String heading, String snippet, double score) { }
