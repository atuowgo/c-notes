package com.cnotes.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * 极简 JWT(HS256),手写以零额外依赖:header.payload.signature,均为 base64url。
 * payload 仅含 sub(userId)与 exp(过期秒)。密钥来自 {@code auth.jwt.secret},生产必须经环境变量覆盖。
 */
@Service
public class JwtService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

    private final byte[] secret;

    public JwtService(@Value("${auth.jwt.secret:dev-only-insecure-secret-change-me-in-prod}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(String userId, Duration ttl) {
        long exp = Instant.now().plus(ttl).getEpochSecond();
        String payload = b64("{\"sub\":\"" + userId + "\",\"exp\":" + exp + "}");
        String signingInput = HEADER + "." + payload;
        return signingInput + "." + sign(signingInput);
    }

    /** 校验签名与过期,返回 subject(userId);任何不合法返回空。 */
    public Optional<String> verify(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 3) return Optional.empty();
        String signingInput = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(signingInput), parts[2])) return Optional.empty();
        try {
            String payload = new String(B64D.decode(parts[1]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(extract(payload, "exp"));
            if (Instant.now().getEpochSecond() >= exp) return Optional.empty();
            String sub = extract(payload, "sub");
            return sub.isBlank() ? Optional.empty() : Optional.of(sub);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("jwt sign failed", e);
        }
    }

    private static String b64(String s) { return B64.encodeToString(s.getBytes(StandardCharsets.UTF_8)); }

    /** 从扁平 JSON 取字段值(sub 为字符串、exp 为数字),仅供本类自产 payload 解析用。 */
    private static String extract(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int colon = json.indexOf(':', i);
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        while (end < json.length() && "\",}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
