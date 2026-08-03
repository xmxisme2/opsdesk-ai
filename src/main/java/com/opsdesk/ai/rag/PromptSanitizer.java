package com.opsdesk.ai.rag;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** 向外部模型发送前对问题和知识片段执行不可逆脱敏，映射不落盘。 */
@Component
public class PromptSanitizer {
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}(?![\\d.])");
    private static final Pattern SECRET = Pattern.compile("(?i)(?:api[_-]?key|secret|token|password|authorization)\\s*[:=]\\s*[^\\s,;]+" );
    private static final Pattern CONNECTION = Pattern.compile("(?i)(?:jdbc|mysql|redis|mongodb)://[^\\s]+" );

    public String sanitize(String value) {
        if (value == null || value.isBlank()) return "";
        String sanitized = PHONE.matcher(value).replaceAll("[手机号]");
        sanitized = EMAIL.matcher(sanitized).replaceAll("[邮箱]");
        sanitized = IPV4.matcher(sanitized).replaceAll("[IP地址]");
        sanitized = SECRET.matcher(sanitized).replaceAll("[敏感凭证]");
        return CONNECTION.matcher(sanitized).replaceAll("[连接串]");
    }
}
