package com.qrattendance.backend.service;

import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    public Session createSession(Session session) {
        return sessionRepository.save(session);
    }

    public void deleteSession(String id) {
        sessionRepository.deleteById(id);
    }

    public Optional<Session> findById(String id) {
        return sessionRepository.findById(id);
    }

    public List<Session> getSessionsForSubject(Subject subject) {
        return sessionRepository.findBySubject(subject);
    }

    public Session activateSession(Session session) {
        // PROVJERA: Da li već postoji aktivna sesija za ovaj predmet?
        List<Session> activeOnes = sessionRepository.findBySubjectAndStatus(session.getSubject(), Session.SessionStatus.ACTIVE);
        if (!activeOnes.isEmpty() && !activeOnes.get(0).getId().equals(session.getId())) {
            throw new IllegalStateException("Već postoji aktivna sesija za ovaj predmet.");
        }

        session.setStatus(Session.SessionStatus.ACTIVE);
        session.setActivatedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    public Session closeSession(Session session) {
        session.setStatus(Session.SessionStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    public boolean isActive(Session session) {
        return session.getStatus() == Session.SessionStatus.ACTIVE;
    }
}