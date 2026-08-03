USE opsdesk_ai;

SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'opsdesk_ai'
  AND table_name IN (
    'ai_runtime_config',
    'knowledge_index_state',
    'event_consumption_log',
    'ai_index_task',
    'ai_conversation',
    'ai_message',
    'ai_call_log',
    'ai_call_reference',
    'rag_feedback'
  );

SELECT config_key, config_value
FROM ai_runtime_config
WHERE config_key IN ('ai.enabled', 'ai.rag.enabled')
  AND deleted = 0
ORDER BY config_key;
