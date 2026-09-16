package com.qrattendance.backend.controller;

import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.security.AccessControlService;
import com.qrattendance.backend.service.ExportService;
import com.qrattendance.backend.service.SessionService;
import com.qrattendance.backend.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final SessionService sessionService;
    private final SubjectService subjectService;
    private final AccessControlService accessControlService;

    @Transactional
    @GetMapping("/session/{sessionId}/xlsx")
    public ResponseEntity<byte[]> exportSessionXlsx(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) throws Exception {

        Session session = accessControlService.requireOwnedSession(sessionId, jwt);
        byte[] data = exportService.exportSessionToXlsx(session);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=prisustvo_" + sessionId + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @Transactional
    @GetMapping("/session/{sessionId}/pdf")
    public ResponseEntity<byte[]> exportSessionPdf(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) throws Exception {

        Session session = accessControlService.requireOwnedSession(sessionId, jwt);
        byte[] data = exportService.exportSessionToPdf(session);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=prisustvo_" + sessionId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    @Transactional
    @GetMapping("/subject/{subjectId}/xlsx")
    public ResponseEntity<byte[]> exportSubjectXlsx(
            @PathVariable String subjectId,
            @AuthenticationPrincipal Jwt jwt) throws Exception {

        Subject subject = accessControlService.requireOwnedSubject(subjectId, jwt);
        List<Session> sessions = sessionService.getSessionsForSubject(subject);
        byte[] data = exportService.exportSubjectToXlsx(subject, sessions);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=prisustvo_" + subject.getCode() + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @Transactional
    @GetMapping("/subject/{subjectId}/pdf")
    public ResponseEntity<byte[]> exportSubjectPdf(
            @PathVariable String subjectId,
            @AuthenticationPrincipal Jwt jwt) throws Exception {

        Subject subject = accessControlService.requireOwnedSubject(subjectId, jwt);
        List<Session> sessions = sessionService.getSessionsForSubject(subject);
        byte[] data = exportService.exportSubjectToPdf(subject, sessions);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=prisustvo_" + subject.getCode() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}