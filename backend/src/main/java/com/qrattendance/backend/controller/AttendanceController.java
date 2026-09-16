package com.qrattendance.backend.controller;

import com.qrattendance.backend.dto.AttendanceDTO;
import com.qrattendance.backend.model.Attendance;
import com.qrattendance.backend.model.QrToken;
import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.security.AccessControlService;
import com.qrattendance.backend.security.RateLimiterService;
import com.qrattendance.backend.service.AttendanceService;
import com.qrattendance.backend.service.QrTokenService;
import com.qrattendance.backend.service.SessionService;
import com.qrattendance.backend.service.SubjectService;
import com.qrattendance.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final SessionService sessionService;
    private final UserService userService;
    private final QrTokenService qrTokenService;
    private final SubjectService subjectService;
    private final RateLimiterService rateLimiterService;
    private final AccessControlService accessControlService;

    @PostMapping("/api/student/attendance/checkin")
    public ResponseEntity<?> checkIn(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        String token = body.get("token");
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        String userEmail = jwt.getClaimAsString("email");
        if (!rateLimiterService.tryConsume(userEmail)) {
            return ResponseEntity.status(429)
                    .body(Map.of("error", "Previše pokušaja. Pokušajte ponovo za 30 sekundi."));
        }

        QrToken qrToken;
        try {
            qrToken = qrTokenService.validateToken(token);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token nije validan ili je istekao"));
        }

        Session session = sessionService.findById(qrToken.getSessionId())
                .orElseThrow(() -> new NoSuchElementException("Sesija nije pronađena"));

        if (!sessionService.isActive(session)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sesija nije aktivna"));
        }

        User student = userService.getOrCreateUser(jwt);

        if (!subjectService.isStudentEnrolled(session.getSubject().getId(), student.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nisi upisan na ovaj predmet"));
        }

        Attendance attendance = attendanceService.checkIn(session, student, ipAddress, userAgent, token);
        return ResponseEntity.ok(Map.of(
                "message", "Prisustvo uspješno evidentirano",
                "checkInTime", attendance.getCheckInTime()
        ));
    }

    @GetMapping("/api/admin/attendance/session/{sessionId}")
    public ResponseEntity<List<AttendanceDTO>> getAttendanceForSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Session session = accessControlService.requireOwnedSession(sessionId, jwt);
        List<Attendance> list = attendanceService.getAttendanceForSession(session);

        List<AttendanceDTO> dtos = list.stream().map(a -> AttendanceDTO.builder()
                .id(a.getId())
                .firstName(a.getStudent().getFirstName())
                .lastName(a.getStudent().getLastName())
                .email(a.getStudent().getEmail())
                .indexNumber(a.getStudent().getIndexNumber())
                .checkInTime(a.getCheckInTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .ipAddress(a.getIpAddress())
                .userAgent(a.getUserAgent())
                .tokenUsed(a.getTokenUsed())
                .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @DeleteMapping("/api/admin/attendance/{attendanceId}")
    public ResponseEntity<?> deleteAttendance(
            @PathVariable String attendanceId,
            @AuthenticationPrincipal Jwt jwt) {

        Attendance attendance = attendanceService.findById(attendanceId)
                .orElseThrow(() -> new NoSuchElementException("Unos nije pronađen"));

        accessControlService.requireOwnedSession(attendance.getSession().getId(), jwt);

        attendanceService.deleteAttendance(attendanceId);
        return ResponseEntity.ok(Map.of("message", "Unos uspješno obrisan"));
    }
}