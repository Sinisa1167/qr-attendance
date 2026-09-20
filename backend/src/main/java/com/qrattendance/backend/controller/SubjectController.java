package com.qrattendance.backend.controller;

import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.security.AccessControlService;
import com.qrattendance.backend.service.SubjectService;
import com.qrattendance.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;
    private final UserService userService;
    private final AccessControlService accessControlService;

    @GetMapping
    public ResponseEntity<List<Subject>> getSubjects(@AuthenticationPrincipal Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.ok(subjectService.getSubjectsForEmployee(user));
    }

    @PostMapping
    public ResponseEntity<Subject> createSubject(
            @RequestBody Subject subject,
            @AuthenticationPrincipal Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        // klijent ne smije birati id (inace bi save prepisao tudji predmet), niti sesije/studente
        subject.setId(null);
        subject.setSessions(new ArrayList<>());
        subject.setStudents(new ArrayList<>());
        subject.setCreatedBy(user);
        return ResponseEntity.ok(subjectService.save(subject));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSubject(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        accessControlService.requireOwnedSubject(id, jwt);
        subjectService.delete(id);
        return ResponseEntity.ok().build();
    }
}
