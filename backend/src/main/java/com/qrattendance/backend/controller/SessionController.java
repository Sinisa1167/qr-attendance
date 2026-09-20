package com.qrattendance.backend.controller;

import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.QrToken;
import com.qrattendance.backend.security.AccessControlService;
import com.qrattendance.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final QrTokenService qrTokenService;
    private final QrSchedulerService qrSchedulerService;
    private final AccessControlService accessControlService;

    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<List<Session>> getSessionsForSubject(
            @PathVariable String subjectId,
            @AuthenticationPrincipal Jwt jwt) {

        Subject subject = accessControlService.requireOwnedSubject(subjectId, jwt);
        return ResponseEntity.ok(sessionService.getSessionsForSubject(subject));
    }

    @PostMapping
    public ResponseEntity<Session> createSession(
            @RequestBody Session session,
            @AuthenticationPrincipal Jwt jwt) {

        if (session.getSubject() == null || session.getSubject().getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Subject subject = accessControlService.requireOwnedSubject(session.getSubject().getId(), jwt);
        session.setSubject(subject);

        // klijent ne odredjuje id/status: sesija uvijek nastaje kao CREATED, a aktivira se samo kroz /activate
        session.setId(null);
        session.setStatus(Session.SessionStatus.CREATED);
        session.setCreatedAt(null);
        session.setActivatedAt(null);
        session.setClosedAt(null);
        session.setAttendanceList(null);

        return ResponseEntity.ok(sessionService.createSession(session));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> deleteSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Session session = accessControlService.requireOwnedSession(sessionId, jwt);

        if (session.getStatus() == Session.SessionStatus.ACTIVE) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ne možete obrisati aktivnu sesiju. Prvo je zatvorite."));
        }

        qrSchedulerService.unregisterSession(sessionId);
        sessionService.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "Sesija uspješno obrisana"));
    }

    @PostMapping("/{sessionId}/activate")
    public ResponseEntity<Map<String, Object>> activateSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Session session = accessControlService.requireOwnedSession(sessionId, jwt);

        Session activated = sessionService.activateSession(session);
        QrToken token = qrTokenService.generateToken(sessionId);
        qrSchedulerService.registerSession(sessionId);

        return ResponseEntity.ok(Map.of(
                "session", activated,
                "token", token.getToken(),
                "expiresAt", token.getExpiresAt()
        ));
    }

    @PostMapping("/{sessionId}/close")
    public ResponseEntity<Session> closeSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Session session = accessControlService.requireOwnedSession(sessionId, jwt);

        qrSchedulerService.unregisterSession(sessionId);
        return ResponseEntity.ok(sessionService.closeSession(session));
    }

    @GetMapping("/{sessionId}/token")
    public ResponseEntity<Map<String, Object>> getCurrentToken(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Session session = accessControlService.requireOwnedSession(sessionId, jwt);

        if (!sessionService.isActive(session)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sesija nije aktivna"));
        }

        // Token i preostalo vrijeme dolaze iz istog objekta, pa se ne mogu razici.
        QrToken current = qrTokenService.getCurrentToken(sessionId);
        if (current == null || QrTokenService.remainingMillis(current) <= 0) {
            current = qrTokenService.generateToken(sessionId);
        }

        long remainingMs = Math.max(0, QrTokenService.remainingMillis(current));

        return ResponseEntity.ok(Map.of(
                "token", current.getToken(),
                "sessionId", sessionId,
                "expiresIn", (remainingMs + 999) / 1000,
                "expiresInMs", remainingMs
        ));
    }
}
