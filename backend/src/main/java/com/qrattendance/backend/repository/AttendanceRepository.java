package com.qrattendance.backend.repository;

import com.qrattendance.backend.model.Attendance;
import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, String> {
    List<Attendance> findBySession(Session session);
    List<Attendance> findByStudent(User student);
    Optional<Attendance> findBySessionAndStudent(Session session, User student);
    boolean existsBySessionAndStudent(Session session, User student);
}