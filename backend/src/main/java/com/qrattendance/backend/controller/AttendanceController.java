package com.qrattendance.backend.controller;

import com.qrattendance.backend.model.Attendance;
import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.model.QrToken;
import com.qrattendance.backend.service.AttendanceService;
import com.qrattendance.backend.service.SessionService;
import com.qrattendance.backend.service.UserService;
import com.qrattendance.backend.service.QrTokenService;
import com.qrattendance.backend.service.SubjectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final SessionService sessionService;
    private final UserService userService;
    private final QrTokenService qrTokenService;
    private final SubjectService subjectService;

    // student skenira QR kod i evidentira prisustvo
    @PostMapping("/api/student/attendance/checkin")
    public ResponseEntity<?> checkIn(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        String token = body.get("token");
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        // validiraj token
        QrToken qrToken;
        try {
            qrToken = qrTokenService.validateToken(token);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token nije validan ili je istekao"));
        }

        // pronadji sesiju
        Session session = sessionService.findById(qrToken.getSessionId())
                .orElseThrow(() -> new RuntimeException("Sesija nije pronađena"));

        // provjeri da li je sesija aktivna
        if (!sessionService.isActive(session)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sesija nije aktivna"));
        }

        // pronadji studenta
        User student = userService.getOrCreateUser(jwt);

        // Provjeri da li je student upisan na predmet
        if (!subjectService.isStudentEnrolled(session.getSubject(), student)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nisi upisan na ovaj predmet"));
        }

        // evidentiraj prisustvo
        try {
            Attendance attendance = attendanceService.checkIn(session, student, ipAddress, userAgent, token);
            return ResponseEntity.ok(Map.of(
                    "message", "Prisustvo uspješno evidentirano",
                    "checkInTime", attendance.getCheckInTime()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // zaposleni gleda prisustvo za sesiju
    @GetMapping("/api/admin/attendance/session/{sessionId}")
    public ResponseEntity<List<Attendance>> getAttendanceForSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Session session = sessionService.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Sesija nije pronađena"));

        return ResponseEntity.ok(attendanceService.getAttendanceForSession(session));
    }

    // zaposleni brise unos prisustva
    @DeleteMapping("/api/admin/attendance/{attendanceId}")
    public ResponseEntity<?> deleteAttendance(
            @PathVariable String attendanceId,
            @AuthenticationPrincipal Jwt jwt) {

        attendanceService.findById(attendanceId)
                .orElseThrow(() -> new RuntimeException("Unos nije pronađen"));

        attendanceService.deleteAttendance(attendanceId);
        return ResponseEntity.ok(Map.of("message", "Unos uspješno obrisan"));
    }
}