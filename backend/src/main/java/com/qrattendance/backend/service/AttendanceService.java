package com.qrattendance.backend.service;

import com.qrattendance.backend.model.Attendance;
import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<Attendance> getAttendanceForSession(Session session) {
        return attendanceRepository.findBySession(session);
    }

    public List<Attendance> getAttendanceForStudent(User student) {
        return attendanceRepository.findByStudent(student);
    }

    public boolean hasStudentCheckedIn(Session session, User student) {
        return attendanceRepository.existsBySessionAndStudent(session, student);
    }

        @Transactional
    public Attendance checkIn(Session session, User student, String ipAddress, String userAgent, String tokenUsed) {
        if (session.getStatus() != Session.SessionStatus.ACTIVE) {
            throw new IllegalStateException("Sesija nije aktivna");
        }

        if (hasStudentCheckedIn(session, student)) {
            throw new IllegalStateException("Student je već evidentiran u ovoj sesiji");
        }

        Attendance attendance = new Attendance();
        attendance.setSession(session);
        attendance.setStudent(student);
        attendance.setIpAddress(ipAddress);
        attendance.setUserAgent(userAgent);
        attendance.setTokenUsed(tokenUsed);

        Attendance saved;
        try {
            saved = attendanceRepository.saveAndFlush(attendance);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Student je već evidentiran u ovoj sesiji");
        }

        messagingTemplate.convertAndSend(
            "/topic/session/" + session.getId(),
            Map.of(
                "type", "NEW_ATTENDANCE",
                "sessionId", session.getId()
            )
        );

        return saved;
    }

    public void deleteAttendance(String id) {
        attendanceRepository.deleteById(id);
    }

    public Optional<Attendance> findById(String id) {
        return attendanceRepository.findById(id);
    }
}