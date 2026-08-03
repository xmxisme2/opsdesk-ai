package com.opsdesk.ai.rag;

/** 受控 Chat 模型抽象，业务层不得直接依赖具体供应商 HTTP 协议。 */
public interface ChatGateway {
    String chat(String systemPrompt, String userQuestion);
}
