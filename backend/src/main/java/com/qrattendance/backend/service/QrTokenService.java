package com.qrattendance.backend.service;

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
    private static final String TOKEN_PREFIX = "qr:token:";
    private static final String SESSION_PREFIX = "qr:session:";

    @Value("${app.qr-refresh-interval:30000}")
    private long qrRefreshIntervalMs;

    private long getTokenValiditySeconds() {
      return qrRefreshIntervalMs / 1000;
    }

    public QrToken generateToken(String sessionId) {
        // Invalidate stari token za ovu sesiju
        invalidateSessionToken(sessionId);

        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        QrToken qrToken = new QrToken(
            token,
            sessionId,
            now,
            now.plusSeconds(getTokenValiditySeconds()),
            false
        );

        // cuvaj token u redisu sa TTL
        redisTemplate.opsForValue().set(
            TOKEN_PREFIX + token,
            qrToken,
            getTokenValiditySeconds(),
            TimeUnit.SECONDS
        );

        // cuvaj mapiranje sesija - token
        redisTemplate.opsForValue().set(
            SESSION_PREFIX + sessionId,
            token,
            getTokenValiditySeconds(),
            TimeUnit.SECONDS
        );

        return qrToken;
    }

    public QrToken validateToken(String token) {
        Object obj = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (obj == null) {
            throw new IllegalStateException("Token nije validan ili je istekao");
        }
        
        // Konvertuj LinkedHashMap u QrToken
        if (obj instanceof QrToken) {
            return (QrToken) obj;
        }
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        return mapper.convertValue(obj, QrToken.class);
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
}