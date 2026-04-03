package com.qrattendance.backend.service;

import com.qrattendance.backend.model.QrToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class QrTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final long TOKEN_VALIDITY_SECONDS = 30;
    private static final String TOKEN_PREFIX = "qr:token:";
    private static final String SESSION_PREFIX = "qr:session:";

    public QrToken generateToken(String sessionId) {
        // Invalidate stari token za ovu sesiju
        invalidateSessionToken(sessionId);

        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        QrToken qrToken = new QrToken(
            token,
            sessionId,
            now,
            now.plusSeconds(TOKEN_VALIDITY_SECONDS),
            false
        );

        // cuvaj token u redisu sa TTL
        redisTemplate.opsForValue().set(
            TOKEN_PREFIX + token,
            qrToken,
            TOKEN_VALIDITY_SECONDS,
            TimeUnit.SECONDS
        );

        // cuvaj mapiranje sesija - token
        redisTemplate.opsForValue().set(
            SESSION_PREFIX + sessionId,
            token,
            TOKEN_VALIDITY_SECONDS,
            TimeUnit.SECONDS
        );

        return qrToken;
    }

    public QrToken validateToken(String token) {
        Object obj = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (obj == null) {
            throw new IllegalStateException("Token nije validan ili je istekao");
        }
        return (QrToken) obj;
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