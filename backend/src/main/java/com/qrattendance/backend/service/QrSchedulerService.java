package com.qrattendance.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class QrSchedulerService {

    private final QrTokenService qrTokenService;
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public void registerSession(String sessionId) {
        activeSessions.add(sessionId);
    }

    public void unregisterSession(String sessionId) {
        activeSessions.remove(sessionId);
        qrTokenService.invalidateSessionToken(sessionId);
    }

    @Scheduled(fixedRateString = "${app.qr-refresh-interval:30000}")
    public void refreshTokens() {
        for (String sessionId : activeSessions) {
            try {
                qrTokenService.generateToken(sessionId);
            } catch (Exception e) {
                activeSessions.remove(sessionId);
            }
        }
    }
}