package com.opsdesk.ai.health.service;

import com.opsdesk.ai.health.vo.AiServiceHealthVO;

/**
 * AI 服务健康检查服务。
 */
public interface AiHealthService {

    AiServiceHealthVO check();
}
