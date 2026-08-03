package com.opsdesk.ai.architecture;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 数据库结构规范测试。
 */
class AiSchemaPolicyTest {

    private static final List<String> TABLES = List.of(
            "ai_runtime_config",
            "knowledge_index_state",
            "event_consumption_log",
            "ai_index_task",
            "ai_conversation",
            "ai_message",
            "ai_call_log",
            "ai_call_reference",
            "rag_feedback"
    );

    @Test
    void everyAiBusinessTableShouldContainCommonFields() throws Exception {
        String sql = Files.readString(Path.of("sql", "01_schema.sql"), StandardCharsets.UTF_8);

        for (String table : TABLES) {
            String body = tableBody(sql, table);
            assertTrue(body.contains("id BIGINT"), table + " 缺少 id");
            assertTrue(body.contains("create_time DATETIME"), table + " 缺少 create_time");
            assertTrue(body.contains("update_time DATETIME"), table + " 缺少 update_time");
            assertTrue(body.contains("create_by BIGINT"), table + " 缺少 create_by");
            assertTrue(body.contains("update_by BIGINT"), table + " 缺少 update_by");
            assertTrue(body.contains("deleted TINYINT"), table + " 缺少 deleted");
        }
        assertEquals(9, TABLES.size());
    }

    private String tableBody(String sql, String table) {
        String marker = "CREATE TABLE IF NOT EXISTS " + table + " (";
        int start = sql.indexOf(marker);
        assertTrue(start >= 0, "缺少表：" + table);
        int end = sql.indexOf(") COMMENT=", start);
        assertTrue(end > start, "表定义未闭合：" + table);
        return sql.substring(start, end);
    }
}
