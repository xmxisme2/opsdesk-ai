-- OpsDesk AI 独立数据库建表脚本。
-- 注意：AI 服务只允许连接 opsdesk_ai，不得跨库读取 opsdesk 业务表。

CREATE DATABASE IF NOT EXISTS opsdesk_ai
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE opsdesk_ai;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ai_runtime_config (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
  config_key VARCHAR(128) NOT NULL COMMENT '配置键',
  config_value TEXT NULL COMMENT '非敏感配置值',
  value_type VARCHAR(32) NOT NULL COMMENT 'STRING/INTEGER/DECIMAL/BOOLEAN/JSON',
  config_group VARCHAR(64) NOT NULL COMMENT 'GENERAL/RAG/MODEL/INDEX/SECURITY',
  description VARCHAR(500) NULL COMMENT '中文说明',
  editable TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许在线编辑',
  config_version BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  active_config_key VARCHAR(128)
    GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN config_key ELSE NULL END) STORED
    COMMENT '仅活动数据参与唯一约束',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人 ID',
  update_by BIGINT NULL COMMENT '更新人 ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
  UNIQUE KEY uk_ai_runtime_config_active_key (active_config_key),
  KEY idx_ai_runtime_config_group (config_group, deleted)
) COMMENT='AI 非敏感运行配置';

CREATE TABLE IF NOT EXISTS knowledge_index_state (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
  article_id BIGINT NOT NULL COMMENT 'OpsDesk 文章 ID',
  article_version BIGINT NOT NULL COMMENT '文章业务版本',
  content_hash VARCHAR(128) NOT NULL COMMENT '索引内容哈希',
  article_status VARCHAR(32) NOT NULL COMMENT '文章状态快照',
  index_status VARCHAR(32) NOT NULL COMMENT 'PENDING/PROCESSING/INDEXED/REMOVED/FAILED',
  index_name VARCHAR(128) NULL COMMENT '实际索引名',
  index_version VARCHAR(32) NULL COMMENT 'Mapping 与切分版本',
  embedding_provider VARCHAR(64) NULL COMMENT 'Embedding 提供方',
  embedding_model VARCHAR(128) NULL COMMENT 'Embedding 模型',
  embedding_dimensions INT NULL COMMENT '向量维度',
  chunk_count INT NOT NULL DEFAULT 0 COMMENT '成功分块数',
  last_event_id VARCHAR(64) NULL COMMENT '最近处理事件 ID',
  indexed_time DATETIME NULL COMMENT '成功索引时间',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '索引重试次数',
  last_error VARCHAR(1000) NULL COMMENT '最近脱敏错误摘要',
  active_article_id BIGINT
    GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN article_id ELSE NULL END) STORED
    COMMENT '仅活动数据参与唯一约束',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人 ID',
  update_by BIGINT NULL COMMENT '更新人 ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
  UNIQUE KEY uk_knowledge_index_state_article (active_article_id),
  KEY idx_knowledge_index_state_status (index_status, update_time),
  KEY idx_knowledge_index_state_model (embedding_model, index_version)
) COMMENT='知识文章索引状态';

CREATE TABLE IF NOT EXISTS event_consumption_log (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
  event_id VARCHAR(64) NOT NULL COMMENT '全局事件 ID 和消费幂等键',
  event_type VARCHAR(128) NOT NULL COMMENT '事件类型',
  aggregate_id BIGINT NULL COMMENT '业务聚合 ID',
  consumer_name VARCHAR(128) NOT NULL COMMENT '消费者名称',
  consume_status VARCHAR(32) NOT NULL COMMENT 'PROCESSING/SUCCESS/FAILED',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '消费重试次数',
  consumed_time DATETIME NULL COMMENT '消费成功时间',
  last_error VARCHAR(1000) NULL COMMENT '最近脱敏错误摘要',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人 ID',
  update_by BIGINT NULL COMMENT '更新人 ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
  UNIQUE KEY uk_event_consumption_log_event_id (event_id),
  KEY idx_event_consumption_log_status (consume_status, update_time),
  KEY idx_event_consumption_log_aggregate (aggregate_id, create_time)
) COMMENT='RabbitMQ 事件消费幂等日志';

CREATE TABLE IF NOT EXISTS ai_index_task (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
  task_type VARCHAR(32) NOT NULL COMMENT 'ARTICLE_REINDEX/FULL_REBUILD/RECONCILE',
  article_id BIGINT NULL COMMENT '单篇任务文章 ID',
  task_status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
  request_id VARCHAR(64) NOT NULL COMMENT '请求幂等 ID',
  target_index_name VARCHAR(128) NULL COMMENT '目标索引名',
  total_count INT NOT NULL DEFAULT 0 COMMENT '任务总数',
  success_count INT NOT NULL DEFAULT 0 COMMENT '成功数量',
  failed_count INT NOT NULL DEFAULT 0 COMMENT '失败数量',
  started_time DATETIME NULL COMMENT '开始时间',
  finished_time DATETIME NULL COMMENT '结束时间',
  last_error VARCHAR(1000) NULL COMMENT '最近脱敏错误摘要',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人 ID',
  update_by BIGINT NULL COMMENT '更新人 ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
  UNIQUE KEY uk_ai_index_task_request_id (request_id),
  KEY idx_ai_index_task_status (task_status, create_time),
  KEY idx_ai_index_task_article (article_id, create_time)
) COMMENT='索引重建与对账任务';

CREATE TABLE IF NOT EXISTS ai_conversation (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
  owner_id BIGINT NOT NULL COMMENT '会话所有者 OpsDesk 用户 ID',
  title VARCHAR(200) NOT NULL COMMENT '会话标题',
  scene VARCHAR(64) NOT NULL COMMENT 'AI 场景，首版 KNOWLEDGE_RAG',
  status VARCHAR(32) NOT NULL COMMENT 'ACTIVE/ARCHIVED',
  last_message_time DATETIME NULL COMMENT '最近消息时间',
  message_count INT NOT NULL DEFAULT 0 COMMENT '消息数量',
  expire_time DATETIME NULL COMMENT '自动清理时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人 ID',
  update_by BIGINT NULL COMMENT '更新人 ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
  KEY idx_ai_conversation_owner (owner_id, status, last_message_time),
  KEY idx_ai_conversation_expire (expire_time, deleted)
) COMMENT='AI 会话，阶段 6 启用';

CREATE TABLE IF NOT EXISTS ai_message (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  role VARCHAR(32) NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
  content LONGTEXT NULL COMMENT '脱敏后的消息内容',
  content_hash VARCHAR(128) NULL COMMENT '内容哈希',
  call_log_id BIGINT NULL COMMENT '关联 AI 调用日志',
  sequence_no INT NOT NULL COMMENT '会话内序号',
  insufficient_evidence TINYINT NOT NULL DEFAULT 0 COMMENT '是否证据不足',
  status VARCHAR(32) NOT NULL COMMENT 'SUCCESS/FAILED/BLOCKED/CANCELLED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人 ID',
  update_by BIGINT NULL COMMENT '更新人 ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
  UNIQUE KEY uk_ai_message_sequence (conversation_id, sequence_no, deleted),
  KEY idx_ai_message_call (call_log_id)
) COMMENT='AI 会话消息';

CREATE TABLE IF NOT EXISTS ai_call_log (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
  request_id VARCHAR(64) NOT NULL COMMENT '模型或内部请求 ID',
  trace_id VARCHAR(64) NOT NULL COMMENT '跨服务 TraceId',
  scene VARCHAR(64) NOT NULL COMMENT 'AI 场景',
  biz_type VARCHAR(64) NULL COMMENT '关联业务类型',
  biz_id BIGINT NULL COMMENT '关联业务 ID',
  operator_id BIGINT NULL COMMENT '调用用户 ID',
  conversation_id BIGINT NULL COMMENT '可选会话 ID',
  provider VARCHAR(64) NULL COMMENT 'Chat 提供方',
  model VARCHAR(128) NULL COMMENT 'Chat 模型',
  embedding_provider VARCHAR(64) NULL COMMENT 'Embedding 提供方',
  embedding_model VARCHAR(128) NULL COMMENT 'Embedding 模型',
  prompt_tokens INT NOT NULL DEFAULT 0 COMMENT '输入 token',
  completion_tokens INT NOT NULL DEFAULT 0 COMMENT '输出 token',
  embedding_tokens INT NOT NULL DEFAULT 0 COMMENT '向量 token',
  cost DECIMAL(14,6) NOT NULL DEFAULT 0 COMMENT '估算成本',
  retrieval_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '检索耗时',
  generation_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '生成耗时',
  duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '总耗时',
  candidate_count INT NOT NULL DEFAULT 0 COMMENT '原始候选数',
  selected_chunk_count INT NOT NULL DEFAULT 0 COMMENT '上下文分块数',
  reference_count INT NOT NULL DEFAULT 0 COMMENT '引用数量',
  desensitized TINYINT NOT NULL DEFAULT 0 COMMENT '是否执行脱敏',
  insufficient_evidence TINYINT NOT NULL DEFAULT 0 COMMENT '是否证据不足',
  success TINYINT NOT NULL DEFAULT 0 COMMENT '是否成功',
  error_code VARCHAR(64) NULL COMMENT '标准错误码',
  error_message VARCHAR(1000) NULL COMMENT '脱敏错误摘要',
  fallback_reason VARCHAR(500) NULL COMMENT '降级或拒答原因',
  config_version BIGINT NOT NULL DEFAULT 1 COMMENT '运行配置版本',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人 ID',
  update_by BIGINT NULL COMMENT '更新人 ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
  UNIQUE KEY uk_ai_call_log_request_id (request_id),
  KEY idx_ai_call_log_scene_time (scene, create_time),
  KEY idx_ai_call_log_operator (operator_id, create_time),
  KEY idx_ai_call_log_conversation (conversation_id, create_time)
) COMMENT='AI 调用审计日志';

CREATE TABLE IF NOT EXISTS ai_call_reference (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
  call_log_id BIGINT NOT NULL COMMENT 'AI 调用日志 ID',
  article_id BIGINT NOT NULL COMMENT 'OpsDesk 文章 ID',
  article_version BIGINT NOT NULL COMMENT '文章版本',
  chunk_id VARCHAR(128) NOT NULL COMMENT 'OpenSearch 分块 ID',
  chunk_no INT NOT NULL COMMENT '分块序号',
  title VARCHAR(200) NOT NULL COMMENT '调用时标题快照',
  heading VARCHAR(300) NULL COMMENT '章节标题',
  snippet VARCHAR(1000) NULL COMMENT '已脱敏引用片段',
  keyword_score DECIMAL(12,6) NULL COMMENT 'BM25 分数',
  vector_score DECIMAL(12,6) NULL COMMENT '向量分数',
  final_score DECIMAL(12,6) NULL COMMENT '融合分数',
  rank_no INT NOT NULL COMMENT '排名',
  selected TINYINT NOT NULL DEFAULT 0 COMMENT '是否进入上下文',
  cited TINYINT NOT NULL DEFAULT 0 COMMENT '是否被最终答案引用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人 ID',
  update_by BIGINT NULL COMMENT '更新人 ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
  KEY idx_ai_call_reference_call (call_log_id, rank_no),
  KEY idx_ai_call_reference_article (article_id, create_time),
  KEY idx_ai_call_reference_chunk (chunk_id)
) COMMENT='AI 调用检索与引用快照';

CREATE TABLE IF NOT EXISTS rag_feedback (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
  message_id BIGINT NOT NULL COMMENT '被评价的助手消息 ID',
  call_log_id BIGINT NOT NULL COMMENT 'AI 调用日志 ID',
  operator_id BIGINT NOT NULL COMMENT '评价用户 ID',
  rating VARCHAR(16) NOT NULL COMMENT 'UP/DOWN',
  reason_code VARCHAR(64) NULL COMMENT '负反馈原因编码',
  comment VARCHAR(1000) NULL COMMENT '可选脱敏说明',
  active_feedback_key VARCHAR(160)
    GENERATED ALWAYS AS (
      CASE WHEN deleted = 0 THEN CONCAT(CAST(message_id AS CHAR), ':', CAST(operator_id AS CHAR)) ELSE NULL END
    ) STORED COMMENT '同一用户对同一消息仅保留一条活动反馈',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人 ID',
  update_by BIGINT NULL COMMENT '更新人 ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
  UNIQUE KEY uk_rag_feedback_active (active_feedback_key),
  KEY idx_rag_feedback_call (call_log_id, create_time),
  KEY idx_rag_feedback_rating (rating, create_time)
) COMMENT='RAG 回答反馈';
