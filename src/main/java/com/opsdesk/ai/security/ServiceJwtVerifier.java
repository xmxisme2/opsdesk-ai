package com.opsdesk.ai.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.config.ServiceJwtProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service JWT 校验器。
 *
 * <p>只接受主应用使用 HS256 签发的短期 SERVICE 令牌，并校验签发方、受众和时间窗口。</p>
 */
@Component
public class ServiceJwtVerifier {

    /** 服务令牌类型：仅允许服务间内部调用，不接受普通用户 access token。 */
    private static final String TOKEN_TYPE_SERVICE = "SERVICE";
    /** 合法调用方服务名：阶段 1 仅允许 OpsDesk 主应用。 */
    private static final String ALLOWED_SERVICE = "opsdesk-backend";
    /** 共享密钥最低长度，避免误用弱口令作为 HMAC 密钥。 */
    private static final int MIN_SECRET_LENGTH = 32;

    private final ObjectMapper objectMapper;
    private final ServiceJwtProperties properties;

    public ServiceJwtVerifier(ObjectMapper objectMapper, ServiceJwtProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ServicePrincipal verify(String token) {
        ensureSecretConfigured();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少 Service JWT");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Service JWT 格式错误");
        }

        Map<String, Object> header = decodeJson(parts[0]);
        if (!"HS256".equals(String.valueOf(header.get("alg")))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Service JWT 算法不受支持");
        }
        String expectedSignature = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Service JWT 签名无效");
        }

        Map<String, Object> payload = decodeJson(parts[1]);
        validateClaims(payload);
        return new ServicePrincipal(
                String.valueOf(payload.get("service")),
                nullableString(payload.get("userId")),
                stringList(payload.get("roles")),
                String.valueOf(payload.get("jti"))
        );
    }

    public boolean isSecretConfigured() {
        return StringUtils.hasText(properties.getSecret())
                && properties.getSecret().length() >= MIN_SECRET_LENGTH;
    }

    private void ensureSecretConfigured() {
        if (!isSecretConfigured()) {
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Service JWT 密钥未配置或长度不足"
            );
        }
    }

    private void validateClaims(Map<String, Object> payload) {
        long now = Instant.now().getEpochSecond();
        long skew = Math.max(0L, properties.getMaxClockSkewSeconds());
        long issuedAt = numberAsLong(payload.get("iat"));
        long expiresAt = numberAsLong(payload.get("exp"));

        if (!properties.getIssuer().equals(String.valueOf(payload.get("iss")))
                || !properties.getAudience().equals(String.valueOf(payload.get("aud")))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Service JWT 签发方或受众无效");
        }
        if (!TOKEN_TYPE_SERVICE.equals(String.valueOf(payload.get("tokenType")))
                || !ALLOWED_SERVICE.equals(String.valueOf(payload.get("service")))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "服务身份无权访问 AI 内部接口");
        }
        if (issuedAt > now + skew || expiresAt <= now - skew || expiresAt - issuedAt > 300L) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Service JWT 已过期或有效期非法");
        }
        if (!StringUtils.hasText(nullableString(payload.get("jti")))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Service JWT 缺少唯一标识");
        }
    }

    private Map<String, Object> decodeJson(String encoded) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Service JWT 载荷无效");
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Service JWT 校验失败");
        }
    }

    private long numberAsLong(Object value) {
        try {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Service JWT 时间字段无效");
        }
    }

    private String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }
}
