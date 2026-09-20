package com.qrattendance.backend.service;

import com.qrattendance.backend.model.QrToken;
import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrSchedulerService {

    /** Kod se mijenja kad mu ostane manje od ovoga (rezerva za kašnjenje rasporeda). */
    private static final long ROTATE_MARGIN_MS = 1000;

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

    /**
     * Rotacija se vodi stvarnim rokom trajanja trenutnog tokena, a ne fiksnim taktom,
     * pa nema razlike u fazi izmedju aktivacije, rotacije i prikaza.
     * Ako Redis privremeno padne, sesija ostaje registrovana i pokušaj se ponavlja.
     */
    @Scheduled(fixedDelay = 500)
    public void rotateExpiringTokens() {
        for (String sessionId : activeSessions) {
            try {
                QrToken current = qrTokenService.getCurrentToken(sessionId);
                if (current == null || QrTokenService.remainingMillis(current) <= ROTATE_MARGIN_MS) {
                    qrTokenService.generateToken(sessionId);
                }
            } catch (Exception e) {
                log.warn("QR rotacija za sesiju {} nije uspjela, ponovni pokušaj za 500 ms: {}",
                        sessionId, e.getMessage());
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void autoCloseExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        for (Session s : sessionRepository.findByStatus(Session.SessionStatus.ACTIVE)) {
            if (s.getDate() == null || s.getEndTime() == null) continue;
            if (s.getDate().atTime(s.getEndTime()).isBefore(now)) {
                s.setStatus(Session.SessionStatus.CLOSED);
                s.setClosedAt(now);
                sessionRepository.save(s);
                unregisterSession(s.getId());
            }
        }
    }
}
