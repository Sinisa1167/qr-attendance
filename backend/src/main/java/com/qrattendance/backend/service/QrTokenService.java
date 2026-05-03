package com.qrattendance.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrattendance.backend.model.QrToken;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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

    @Value("${app.qr-secret}")
    private String qrSecret;

    @Value("${app.qr-refresh-interval:30000}")
    private long qrRefreshIntervalMs;

    public QrToken generateToken(String sessionId) {
        invalidateSessionToken(sessionId);

        String rawToken = UUID.randomUUID().toString();
        String dataToSign = rawToken + ":" + sessionId;
        String signature = computeHmac(dataToSign);
        String fullToken = rawToken + "." + signature;

        long validitySeconds = qrRefreshIntervalMs / 1000;

        QrToken qrToken = new QrToken(
                fullToken,
                sessionId,
                LocalDateTime.now(),
                LocalDateTime.now().plusSeconds(validitySeconds),
                false
        );

        redisTemplate.opsForValue().set(TOKEN_PREFIX + fullToken, qrToken, validitySeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, fullToken, validitySeconds, TimeUnit.SECONDS);

        return qrToken;
    }

    public QrToken validateToken(String token) {
        if (token == null || !token.contains(".")) {
            throw new IllegalArgumentException("Token nije u ispravnom formatu");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Token nije validan");
        }

        String rawToken = parts[0];
        String receivedSignature = parts[1];

        // Dobavljamo sessionId iz Redisa
        String sessionId = getSessionIdFromToken(token);
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalStateException("Token je istekao ili ne postoji");
        }

        // Provjera potpisa
        String expectedSignature = computeHmac(rawToken + ":" + sessionId);
        if (!expectedSignature.equals(receivedSignature)) {
            throw new SecurityException("Neispravan HMAC potpis");
        }

        // Ako je potpis OK, učitavamo iz Redisa
        Object obj = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (obj == null) {
            throw new IllegalStateException("Token je istekao");
        }

        return objectMapper.convertValue(obj, QrToken.class);
    }

    private String getSessionIdFromToken(String token) {
        Object obj = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (obj == null) return null;
        QrToken qrToken = objectMapper.convertValue(obj, QrToken.class);
        return qrToken.getSessionId();
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(
                qrSecret.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA512"
            );
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Greška pri računanju HMAC-a", e);
        }
    }

    public void invalidateSessionToken(String sessionId) {
        Object oldToken = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (oldToken != null) {
            redisTemplate.delete(TOKEN_PREFIX + oldToken);
            redisTemplate.delete(SESSION_PREFIX + sessionId);
        }
    }

    public String getCurrentTokenForSession(String sessionId) {
        Object token = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        return token != null ? token.toString() : null;
    }
}