package com.qrattendance.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrattendance.backend.model.QrToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class QrTokenService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOKEN_PREFIX = "qr:token:";
    private static final String SESSION_PREFIX = "qr:session:";

    @Value("${app.qr-refresh-interval:30000}")
    private long qrRefreshIntervalMs;

    @Value("${app.qr-secret:default-secret-change-in-production}")
    private String qrSecret;

    public QrToken generateToken(String sessionId) {
        invalidateSessionToken(sessionId);

        String rawToken = UUID.randomUUID().toString();
        String signature = computeHmac(rawToken + ":" + sessionId);
        String token = rawToken + "." + signature;

        long validitySeconds = qrRefreshIntervalMs / 1000;

        QrToken qrToken = new QrToken(
            token,
            sessionId,
            LocalDateTime.now(),
            LocalDateTime.now().plusSeconds(validitySeconds),
            false
        );

        redisTemplate.opsForValue().set(
            TOKEN_PREFIX + token,
            qrToken,
            validitySeconds,
            TimeUnit.SECONDS
        );

        redisTemplate.opsForValue().set(
            SESSION_PREFIX + sessionId,
            token,
            validitySeconds,
            TimeUnit.SECONDS
        );

        return qrToken;
    }

    public QrToken validateToken(String token) {
        Object obj = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (obj == null) {
            throw new IllegalStateException("Token nije validan ili je istekao");
        }

        QrToken qrToken = objectMapper.convertValue(obj, QrToken.class);

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new IllegalStateException("Token nije validan");
        }

        String expectedSignature = computeHmac(parts[0] + ":" + qrToken.getSessionId());
        if (!expectedSignature.equals(parts[1])) {
            throw new IllegalStateException("Token nije validan");
        }

        return qrToken;
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                qrSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException("Greška pri računanju HMAC-a", e);
        }
    }

    public void invalidateSessionToken(String sessionId) {
        Object oldToken = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (oldToken != null) {
            redisTemplate.delete(TOKEN_PREFIX + oldToken.toString());
            redisTemplate.delete(SESSION_PREFIX + sessionId);
        }
    }

    public String getCurrentTokenForSession(String sessionId) {
        Object token = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        return token != null ? token.toString() : null;
    }

    public long getRefreshIntervalSeconds() {
        return qrRefreshIntervalMs / 1000;
    }
}