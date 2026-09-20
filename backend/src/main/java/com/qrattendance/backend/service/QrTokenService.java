package com.qrattendance.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrattendance.backend.model.QrToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrTokenService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

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

        Duration validity = Duration.ofMillis(qrRefreshIntervalMs);
        LocalDateTime now = LocalDateTime.now();

        QrToken qrToken = new QrToken(
            token,
            sessionId,
            now,
            now.plus(validity),
            false
        );

        redisTemplate.opsForValue().set(TOKEN_PREFIX + token, qrToken, validity);
        redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, token, validity);

        notifyRotation(sessionId);
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
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
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

    /**
     * Trenutni token sesije zajedno sa vlastitim rokom trajanja (isti objekat, pa token i
     * preostalo vrijeme uvijek pripadaju jedno drugom). Vraca null ako token ne postoji.
     */
    public QrToken getCurrentToken(String sessionId) {
        Object token = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (token == null) return null;
        Object obj = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (obj == null) return null;
        return objectMapper.convertValue(obj, QrToken.class);
    }

    /** Preostalo vrijeme vazenja tokena u milisekundama (serversko vrijeme). */
    public static long remainingMillis(QrToken token) {
        return Duration.between(LocalDateTime.now(), token.getExpiresAt()).toMillis();
    }


    /** Signal live prikazu da povuce novi kod. Ne sadrzi token, pa nista osjetljivo ne ide kroz WebSocket. */
    private void notifyRotation(String sessionId) {
        try {
            messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                Map.of("type", "QR_ROTATED", "sessionId", sessionId)
            );
        } catch (Exception e) {
            log.warn("Nije moguće poslati QR_ROTATED za sesiju {}: {}", sessionId, e.getMessage());
        }
    }
}
