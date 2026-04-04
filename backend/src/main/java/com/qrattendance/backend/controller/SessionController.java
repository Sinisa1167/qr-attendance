package com.qrattendance.backend.controller;

import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.model.QrToken;
import com.qrattendance.backend.service.SessionService;
import com.qrattendance.backend.service.SubjectService;
import com.qrattendance.backend.service.UserService;
import com.qrattendance.backend.service.QrTokenService;
import com.qrattendance.backend.service.QrSchedulerService;
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
    private final UserService userService;
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

        return ResponseEntity.ok(sessionService.createSession(session));
    }

    @PostMapping("/{sessionId}/activate")
    public ResponseEntity<Map<String, Object>> activateSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Session session = sessionService.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Sesija nije pronađena"));

        Session activated = sessionService.activateSession(session);
        QrToken token = qrTokenService.generateToken(sessionId);

        // Registruj sesiju za automatsko osvježavanje tokena
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

        // Unregistruj sesiju i invaliduj token
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
                "sessionId", sessionId
        ));
    }
}