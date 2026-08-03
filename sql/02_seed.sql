-- OpsDesk AI 独立数据库初始化配置。
-- 所有密钥和连接密码必须来自环境变量，禁止在本表中保存。

USE opsdesk_ai;
SET NAMES utf8mb4;

INSERT INTO ai_runtime_config (
  id, config_key, config_value, value_type, config_group, description,
  editable, config_version, create_by, update_by, deleted
) VALUES
  (1001, 'ai.enabled', 'false', 'BOOLEAN', 'GENERAL', 'AI 总开关，完成安全和质量验收前保持关闭', 1, 1, NULL, NULL, 0),
  (1002, 'ai.rag.enabled', 'false', 'BOOLEAN', 'RAG', '知识库 RAG 开关，依赖 AI 总开关', 1, 1, NULL, NULL, 0),
  (1003, 'ai.rag.top_k', '6', 'INTEGER', 'RAG', '最终上下文候选分块数', 1, 1, NULL, NULL, 0),
  (1004, 'ai.rag.candidate_k', '20', 'INTEGER', 'RAG', '混合检索初始候选数', 1, 1, NULL, NULL, 0),
  (1005, 'ai.rag.score_threshold', '0.65', 'DECIMAL', 'RAG', '证据不足判定阈值', 1, 1, NULL, NULL, 0),
  (1006, 'ai.rag.max_chunks_per_article', '2', 'INTEGER', 'RAG', '每篇文章最多进入上下文的分块数', 1, 1, NULL, NULL, 0),
  (1007, 'ai.rag.context_token_budget', '6000', 'INTEGER', 'RAG', '知识上下文 token 预算', 1, 1, NULL, NULL, 0),
  (1008, 'ai.rag.conversation_enabled', 'false', 'BOOLEAN', 'GENERAL', '多轮会话开关，阶段 6 启用', 1, 1, NULL, NULL, 0),
  (1009, 'ai.rag.conversation_retention_days', '30', 'INTEGER', 'GENERAL', '会话默认保留天数', 1, 1, NULL, NULL, 0),
  (1010, 'ai.index.chunk_size_tokens', '500', 'INTEGER', 'INDEX', 'Markdown 默认分块 token 数', 1, 1, NULL, NULL, 0),
  (1011, 'ai.index.chunk_overlap_tokens', '80', 'INTEGER', 'INDEX', '相邻分块重叠 token 数', 1, 1, NULL, NULL, 0),
  (1012, 'ai.index.embedding_model', 'lke-text-embedding-v2', 'STRING', 'MODEL', 'Embedding 模型名称，不包含密钥', 0, 1, NULL, NULL, 0),
  (1013, 'ai.index.embedding_dimensions', NULL, 'INTEGER', 'MODEL', '真实健康检查后锁定的向量维度', 0, 1, NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value),
  value_type = VALUES(value_type),
  config_group = VALUES(config_group),
  description = VALUES(description),
  editable = VALUES(editable),
  update_time = CURRENT_TIMESTAMP,
  deleted = 0;
