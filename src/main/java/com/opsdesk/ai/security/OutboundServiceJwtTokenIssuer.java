package com.opsdesk.ai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.config.ServiceJwtProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** AI 服务调用主应用内部接口时使用的短期 Service JWT 签发器。 */
@Component
public class OutboundServiceJwtTokenIssuer {
    private static final int MIN_SECRET_LENGTH = 32;
    private final ObjectMapper objectMapper;
    private final ServiceJwtProperties properties;

    public OutboundServiceJwtTokenIssuer(ObjectMapper objectMapper, ServiceJwtProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String issue() {
        if (!StringUtils.hasText(properties.getSecret()) || properties.getSecret().length() < MIN_SECRET_LENGTH) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "Service JWT 密钥未配置或长度不足");
        }
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("jti", UUID.randomUUID().toString().replace("-", ""));
        claims.put("iss", "opsdesk-ai-service");
        claims.put("aud", "opsdesk-backend");
        claims.put("sub", "opsdesk-ai-service");
        claims.put("service", "opsdesk-ai-service");
        claims.put("tokenType", "SERVICE");
        claims.put("iat", now);
        claims.put("exp", now + 60L);
        String header = encode(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encode(claims);
        String content = header + "." + payload;
        return content + "." + sign(content);
    }

    private String encode(Object value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Service JWT 载荷序列化失败");
        }
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Service JWT 签名失败");
        }
    }
}
