package com.qrattendance.backend.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private static final int MAX_REQUESTS = 3;
    private static final long WINDOW_SECONDS = 30;

    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> windowStart = new ConcurrentHashMap<>();

    public boolean tryConsume(String key) {
        Instant now = Instant.now();
        
        windowStart.putIfAbsent(key, now);
        requestCounts.putIfAbsent(key, new AtomicInteger(0));

        Instant start = windowStart.get(key);
        
        // Reset window ako je proslo 30 sekundi
        if (now.getEpochSecond() - start.getEpochSecond() >= WINDOW_SECONDS) {
            windowStart.put(key, now);
            requestCounts.put(key, new AtomicInteger(0));
        }

        int count = requestCounts.get(key).incrementAndGet();
        return count <= MAX_REQUESTS;
    }
}