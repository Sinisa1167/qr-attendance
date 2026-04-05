package com.qrattendance.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrattendance.backend.model.QrToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
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

    public QrToken generateToken(String sessionId) {
        invalidateSessionToken(sessionId);

        String token = UUID.randomUUID().toString();
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
        return objectMapper.convertValue(obj, QrToken.class);
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