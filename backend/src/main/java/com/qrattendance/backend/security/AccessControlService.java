package com.qrattendance.backend.security;

import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.service.SessionService;
import com.qrattendance.backend.service.SubjectService;
import com.qrattendance.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final SubjectService subjectService;
    private final SessionService sessionService;
    private final UserService userService;

    public Subject requireOwnedSubject(String subjectId, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        Subject subject = subjectService.findById(subjectId)
                .orElseThrow(() -> new NoSuchElementException("Predmet nije pronađen"));
        assertOwner(subject, user);
        return subject;
    }

    public Session requireOwnedSession(String sessionId, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        Session session = sessionService.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Sesija nije pronađena"));
        assertOwner(session.getSubject(), user);
        return session;
    }

    private void assertOwner(Subject subject, User user) {
        if (subject == null || subject.getCreatedBy() == null
                || !subject.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("Nemate pristup ovom resursu");
        }
    }
}