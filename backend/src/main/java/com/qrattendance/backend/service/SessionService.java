package com.qrattendance.backend.service;

import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    @Transactional
    public Session createSession(Session session) {
        if (session.getStatus() == null) {
            session.setStatus(Session.SessionStatus.CREATED);
        }
        return sessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(String id) {
        sessionRepository.deleteById(id);
    }

    public Optional<Session> findById(String id) {
        return sessionRepository.findById(id);
    }

    public List<Session> getSessionsForSubject(Subject subject) {
        return sessionRepository.findBySubjectOrderByCreatedAtDesc(subject);
    }   

    @Transactional
    public Session activateSession(Session session) {
        List<Session> activeOnes = sessionRepository.findBySubjectAndStatus(session.getSubject(), Session.SessionStatus.ACTIVE);
        
        if (!activeOnes.isEmpty() && !activeOnes.get(0).getId().equals(session.getId())) {
            throw new IllegalStateException("Već postoji aktivna sesija za ovaj predmet.");
        }

        session.setStatus(Session.SessionStatus.ACTIVE);
        session.setActivatedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public Session closeSession(Session session) {
        session.setStatus(Session.SessionStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    public boolean isActive(Session session) {
        return session.getStatus() == Session.SessionStatus.ACTIVE;
    }
}