package com.qrattendance.backend.repository;

import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {

    List<Session> findBySubjectOrderByCreatedAtDesc(Subject subject);
    
    List<Session> findByStatus(Session.SessionStatus status);
    
    List<Session> findBySubjectAndStatus(Subject subject, Session.SessionStatus status);
}