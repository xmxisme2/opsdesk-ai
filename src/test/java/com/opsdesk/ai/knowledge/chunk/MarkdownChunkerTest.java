package com.opsdesk.ai.knowledge.chunk;

import com.opsdesk.ai.knowledge.client.KnowledgeSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Markdown 标题、代码块和稳定编号分块测试。 */
class MarkdownChunkerTest {
    private final MarkdownChunker chunker = new MarkdownChunker();

    @Test
    void shouldSplitLongSectionsAndKeepCodeFenceTogether() {
        String longSection = "网络故障排查步骤。".repeat(90);
        String content = "# VPN 排障\n\n" + longSection
                + "\n\n## 命令检查\n\n```bash\nping gateway\ntraceroute gateway\n```\n\n" + longSection;

        List<KnowledgeChunk> chunks = chunker.chunk(snapshot(content));

        assertTrue(chunks.size() >= 2);
        assertEquals(0, chunks.get(0).chunkNo());
        assertEquals(chunks.size() - 1, chunks.get(chunks.size() - 1).chunkNo());
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains(
                "```bash\nping gateway\ntraceroute gateway\n```")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.embeddingText().startsWith("VPN 连接问题")));
    }

    @Test
    void sameInputShouldProduceStableHashes() {
        List<KnowledgeChunk> first = chunker.chunk(snapshot("# 标题\n\n正文内容"));
        List<KnowledgeChunk> second = chunker.chunk(snapshot("# 标题\n\n正文内容"));
        assertEquals(first, second);
    }

    private KnowledgeSnapshot snapshot(String content) {
        return new KnowledgeSnapshot("1", 1, "VPN 连接问题", "摘要", content, "1", "网络",
                List.of(), null, "PUBLISHED", "ALL_AUTHENTICATED", List.of(), List.of(),
                LocalDateTime.now(), LocalDateTime.now(), "article-hash");
    }
}
