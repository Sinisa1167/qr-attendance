package com.qrattendance.backend.service;

import com.qrattendance.backend.model.Attendance;
import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;

    public List<Attendance> getAttendanceForSession(Session session) {
        return attendanceRepository.findBySession(session);
    }

    public List<Attendance> getAttendanceForStudent(User student) {
        return attendanceRepository.findByStudent(student);
    }

    public boolean hasStudentCheckedIn(Session session, User student) {
        return attendanceRepository.existsBySessionAndStudent(session, student);
    }

    public Attendance checkIn(Session session, User student, String ipAddress, String userAgent, String tokenUsed) {
        if (hasStudentCheckedIn(session, student)) {
            throw new IllegalStateException("Student je već evidentiran u ovoj sesiji");
        }

        if (session.getStatus() != Session.SessionStatus.ACTIVE) {
            throw new IllegalStateException("Sesija nije aktivna");
        }

        Attendance attendance = new Attendance();
        attendance.setSession(session);
        attendance.setStudent(student);
        attendance.setIpAddress(ipAddress);
        attendance.setUserAgent(userAgent);
        attendance.setTokenUsed(tokenUsed);

        return attendanceRepository.save(attendance);
    }

    public void deleteAttendance(String id) {
        attendanceRepository.deleteById(id);
    }

    public Optional<Attendance> findById(String id) {
        return attendanceRepository.findById(id);
    }
}