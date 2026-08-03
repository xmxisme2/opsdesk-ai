package com.opsdesk.ai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.config.ServiceJwtProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service JWT 校验器测试。
 */
class ServiceJwtVerifierTest {

    private static final String SECRET = "opsdesk-ai-service-test-secret-32chars";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldVerifyValidShortLivedServiceToken() {
        ServiceJwtVerifier verifier = new ServiceJwtVerifier(objectMapper, properties(SECRET));
        long now = Instant.now().getEpochSecond();
        String token = token(Map.of(
                "jti", "token-1",
                "iss", "opsdesk-backend",
                "aud", "opsdesk-ai-service",
                "service", "opsdesk-backend",
                "tokenType", "SERVICE",
                "userId", "1001",
                "roles", List.of("ADMIN"),
                "iat", now,
                "exp", now + 60
        ), SECRET);

        ServicePrincipal principal = verifier.verify(token);

        assertEquals("opsdesk-backend", principal.serviceName());
        assertEquals("1001", principal.userId());
        assertEquals(List.of("ADMIN"), principal.roles());
    }

    @Test
    void shouldFailSafelyWhenSecretIsMissing() {
        ServiceJwtVerifier verifier = new ServiceJwtVerifier(objectMapper, properties(""));

        BusinessException exception = assertThrows(BusinessException.class, () -> verifier.verify("any"));

        assertEquals(ErrorCode.AI_SERVICE_UNAVAILABLE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("密钥未配置"));
    }

    @Test
    void shouldRejectExpiredToken() {
        ServiceJwtVerifier verifier = new ServiceJwtVerifier(objectMapper, properties(SECRET));
        long now = Instant.now().getEpochSecond();
        String token = token(Map.of(
                "jti", "token-2",
                "iss", "opsdesk-backend",
                "aud", "opsdesk-ai-service",
                "service", "opsdesk-backend",
                "tokenType", "SERVICE",
                "iat", now - 120,
                "exp", now - 60
        ), SECRET);

        BusinessException exception = assertThrows(BusinessException.class, () -> verifier.verify(token));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    private ServiceJwtProperties properties(String secret) {
        ServiceJwtProperties properties = new ServiceJwtProperties();
        properties.setSecret(secret);
        return properties;
    }

    private String token(Map<String, Object> payload, String secret) {
        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String body = encode(objectMapper.writeValueAsBytes(payload));
            String content = header + "." + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return content + "." + encode(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
