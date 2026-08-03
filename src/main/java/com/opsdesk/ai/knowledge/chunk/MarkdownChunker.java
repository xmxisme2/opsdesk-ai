package com.opsdesk.ai.knowledge.chunk;

import com.opsdesk.ai.knowledge.client.KnowledgeSnapshot;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 面向中文知识文章的 Markdown 分块器。
 *
 * <p>优先按一至三级标题和空行切块，代码块不拆分；目标约 500 token，并复用约 80 token 的前文。</p>
 */
@Component
public class MarkdownChunker {
    private static final int TARGET_TOKENS = 500;
    private static final int OVERLAP_TOKENS = 80;
    private static final Pattern HEADING = Pattern.compile("^#{1,3}\\s+.+$");

    public List<KnowledgeChunk> chunk(KnowledgeSnapshot snapshot) {
        List<Block> blocks = parseBlocks(snapshot.content());
        List<String> contents = pack(blocks);
        List<KnowledgeChunk> chunks = new ArrayList<>(contents.size());
        for (int index = 0; index < contents.size(); index++) {
            String content = contents.get(index).strip();
            String heading = findHeading(content);
            String embeddingText = joinNonBlank(snapshot.title(), heading, content);
            chunks.add(new KnowledgeChunk(index, heading, content, embeddingText, sha256(content)));
        }
        return chunks;
    }

    private List<Block> parseBlocks(String markdown) {
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n");
        List<Block> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean codeFence = false;
        for (String line : normalized.split("\n", -1)) {
            if (line.stripLeading().startsWith("```")) {
                codeFence = !codeFence;
                appendLine(current, line);
                continue;
            }
            if (!codeFence && (HEADING.matcher(line).matches() || line.isBlank())) {
                flushBlock(blocks, current);
                if (!line.isBlank()) {
                    blocks.add(new Block(line, true));
                }
                continue;
            }
            appendLine(current, line);
        }
        flushBlock(blocks, current);
        if (blocks.isEmpty()) {
            blocks.add(new Block(normalized, false));
        }
        return blocks;
    }

    private List<String> pack(List<Block> blocks) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (Block block : blocks) {
            if (block.heading() && current.length() > 0 && estimateTokens(current.toString()) >= OVERLAP_TOKENS) {
                chunks.add(current.toString().strip());
                current = new StringBuilder(overlapTail(current.toString()));
            }
            int projected = estimateTokens(current + "\n\n" + block.text());
            if (current.length() > 0 && projected > TARGET_TOKENS) {
                chunks.add(current.toString().strip());
                current = new StringBuilder(overlapTail(current.toString()));
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(block.text());
        }
        if (current.length() > 0) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    /** 中文字符按一个 token、连续拉丁字母数字按一个 token 估算，避免引入具体模型分词器耦合。 */
    int estimateTokens(String value) {
        int tokens = 0;
        boolean latinRun = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN) {
                tokens++;
                latinRun = false;
            } else if (Character.isLetterOrDigit(current)) {
                if (!latinRun) {
                    tokens++;
                }
                latinRun = true;
            } else {
                latinRun = false;
            }
        }
        return tokens;
    }

    private String overlapTail(String value) {
        String[] paragraphs = value.split("\n\n");
        StringBuilder tail = new StringBuilder();
        for (int index = paragraphs.length - 1; index >= 0; index--) {
            String candidate = paragraphs[index] + (tail.length() == 0 ? "" : "\n\n" + tail);
            if (estimateTokens(candidate) > OVERLAP_TOKENS && tail.length() > 0) {
                break;
            }
            tail = new StringBuilder(candidate);
        }
        return tail.toString();
    }

    private String findHeading(String content) {
        for (String line : content.split("\n")) {
            if (HEADING.matcher(line).matches()) {
                return line.replaceFirst("^#{1,3}\\s+", "").strip();
            }
        }
        return "";
    }

    private String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.strip());
            }
        }
        return String.join("\n", parts);
    }

    private void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private void flushBlock(List<Block> blocks, StringBuilder builder) {
        if (builder.length() > 0) {
            blocks.add(new Block(builder.toString().strip(), false));
            builder.setLength(0);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("知识分块摘要计算失败", exception);
        }
    }

    private record Block(String text, boolean heading) { }
}
