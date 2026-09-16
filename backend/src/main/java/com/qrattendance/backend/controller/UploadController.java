package com.qrattendance.backend.controller;

import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.service.ExcelParserService;
import com.qrattendance.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/upload")
@RequiredArgsConstructor
public class UploadController {

    private final ExcelParserService excelParserService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fajl je prazan"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".csv"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Dozvoljeni su samo .xlsx i .csv fajlovi"));
        }

        try {
            User user = userService.getOrCreateUser(jwt);
            Subject subject = excelParserService.parseAndSave(file, user);
            return ResponseEntity.ok(Map.of(
                "message", "Fajl uspješno uploadovan",
                "subject", subject
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Greška pri parsiranju fajla: " + e.getMessage()));
        }
    }
}