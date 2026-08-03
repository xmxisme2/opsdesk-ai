package com.opsdesk.ai.rag;

import java.util.function.Consumer;

/** 受控 Chat 模型抽象，业务层不得直接依赖具体供应商 HTTP 协议。 */
public interface ChatGateway {
    String chat(String systemPrompt, String userQuestion);

    /** 按模型原始增量顺序回调 token，调用方负责客户端中断和事件封装。 */
    String stream(String systemPrompt, String userQuestion, Consumer<String> tokenConsumer);
}
