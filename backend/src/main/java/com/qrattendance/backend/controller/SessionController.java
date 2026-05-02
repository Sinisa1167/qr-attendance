package com.qrattendance.backend.controller;

import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.QrToken;
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
    private final SubjectService subjectService;
    private final QrTokenService qrTokenService;
    private final QrSchedulerService qrSchedulerService;

    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<List<Session>> getSessionsForSubject(
            @PathVariable String subjectId,
            @AuthenticationPrincipal Jwt jwt) {

        Subject subject = subjectService.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("Predmet nije pronađen"));

        return ResponseEntity.ok(sessionService.getSessionsForSubject(subject));
    }

    @PostMapping
    public ResponseEntity<Session> createSession(
            @RequestBody Session session,
            @AuthenticationPrincipal Jwt jwt) {
        
        if (session.getSubject() == null || session.getSubject().getId() == null) {
             return ResponseEntity.badRequest().build();
        }
        
        Subject subject = subjectService.findById(session.getSubject().getId())
                .orElseThrow(() -> new RuntimeException("Predmet nije pronađen"));
        session.setSubject(subject);

        return ResponseEntity.ok(sessionService.createSession(session));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> deleteSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Session session = sessionService.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Sesija nije pronađena"));

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

        Session session = sessionService.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Sesija nije pronađena"));

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

        Session session = sessionService.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Sesija nije pronađena"));

        qrSchedulerService.unregisterSession(sessionId);
        return ResponseEntity.ok(sessionService.closeSession(session));
    }

    @GetMapping("/{sessionId}/token")
    public ResponseEntity<Map<String, Object>> getCurrentToken(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Session session = sessionService.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Sesija nije pronađena"));

        if (!sessionService.isActive(session)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sesija nije aktivna"));
        }

        String currentToken = qrTokenService.getCurrentTokenForSession(sessionId);
        if (currentToken == null) {
            QrToken token = qrTokenService.generateToken(sessionId);
            currentToken = token.getToken();
        }

        return ResponseEntity.ok(Map.of(
                "token", currentToken,
                "sessionId", sessionId,
                "expiresIn", qrTokenService.getRefreshIntervalSeconds() 
        ));
    }
}