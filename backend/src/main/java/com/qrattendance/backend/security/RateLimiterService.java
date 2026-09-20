package com.qrattendance.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Ogranicavanje broja pokusaja u Redisu (atomican INCR + rok trajanja).
 * Radi ispravno i sa vise instanci backenda i prezivljava restart; kljucevi sami istjecu.
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final int MAX_REQUESTS = 3;
    private static final Duration WINDOW = Duration.ofSeconds(30);
    private static final String PREFIX = "rl:checkin:";

    private final StringRedisTemplate redis;

    public boolean tryConsume(String key) {
        String redisKey = PREFIX + key;
        Long count = redis.opsForValue().increment(redisKey);
        if (count == null) return false;

        // prvi zahtjev otvara prozor; ako je ključ ostao bez roka (pad između INCR i EXPIRE), popravi
        if (count == 1) {
            redis.expire(redisKey, WINDOW);
        } else {
            Long ttl = redis.getExpire(redisKey);
            if (ttl == null || ttl < 0) redis.expire(redisKey, WINDOW);
        }
        return count <= MAX_REQUESTS;
    }
}
