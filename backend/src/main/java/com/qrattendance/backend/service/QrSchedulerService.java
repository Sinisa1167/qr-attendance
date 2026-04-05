package com.qrattendance.backend.service;

import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class QrSchedulerService {

    private final QrTokenService qrTokenService;
    private final SessionRepository sessionRepository;
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    @EventListener(ApplicationReadyEvent.class)
    public void recoverActiveSessions() {
        List<Session> sessions = sessionRepository.findByStatus(Session.SessionStatus.ACTIVE);
        sessions.forEach(s -> activeSessions.add(s.getId()));
    }

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