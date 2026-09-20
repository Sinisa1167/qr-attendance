package com.qrattendance.backend.controller;

import com.qrattendance.backend.dto.SubjectAnalyticsDTO;
import com.qrattendance.backend.model.*;
import com.qrattendance.backend.repository.SubjectRepository;
import com.qrattendance.backend.security.AccessControlService;
import com.qrattendance.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SubjectService subjectService;
    private final SubjectRepository subjectRepository;
    private final AccessControlService accessControlService;

    @Value("${app.attendance.threshold:70.0}")
    private double defaultThreshold;

    @Transactional
    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<?> getSubjectAnalytics(
            @PathVariable String subjectId,
            @AuthenticationPrincipal Jwt jwt) {

        Subject subject = accessControlService.requireOwnedSubject(subjectId, jwt);

        var dto = analyticsService.getSubjectAnalytics(subjectId, defaultThreshold);

        Map<String, Object> response = new HashMap<>();
        response.put("subjectName", dto.getSubjectName());
        response.put("groupName", subject.getGroupName());
        response.put("subjectCode", subject.getCode());
        response.put("totalSessions", dto.getTotalSessions());
        response.put("threshold", defaultThreshold);

        response.put("sessionHeaders", dto.getSessionHeaders().stream().map(sh -> {
            Map<String, Object> m = new HashMap<>();
            m.put("sessionId", sh.getSessionId());
            m.put("date", sh.getDate());
            m.put("activityType", sh.getActivityType());
            return m;
        }).collect(Collectors.toList()));

        // ime i prezime iz baze: fullName se ne moze pouzdano razdvojiti kad ime/prezime ima vise rijeci
        Map<String, User> studentsByIndex = new HashMap<>();
        for (User u : subject.getStudents()) {
            if (u.getIndexNumber() != null) studentsByIndex.putIfAbsent(u.getIndexNumber(), u);
        }

        response.put("studentStats", dto.getStudentStats().stream().map(stat -> {
            Map<String, Object> map = new HashMap<>();
            map.put("index", stat.getIndex());
            User u = stat.getIndex() != null ? studentsByIndex.get(stat.getIndex()) : null;
            if (u != null) {
                map.put("firstName", u.getFirstName());
                map.put("lastName", u.getLastName());
            } else {
                String[] nameParts = stat.getFullName().split(" ", 2);
                map.put("firstName", nameParts[0]);
                map.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
            }
            map.put("attended", stat.getAttendedCount());
            map.put("percentage", stat.getPercentage());
            map.put("isCritical", stat.isBelowThreshold());
            map.put("sessionAttendance", stat.getSessionAttendance());
            return map;
        }).collect(Collectors.toList()));

        response.put("chartData", dto.getSessionStats().stream().map(s -> {
            Map<String, Object> point = new HashMap<>();
            point.put("name", "Sesija " + s.getDate());
            point.put("prisutni", s.getCount());
            return point;
        }).collect(Collectors.toList()));

        response.put("groupStats", buildGroupStats(subject, dto));

        return ResponseEntity.ok(response);
    }

    /**
     * Poredjenje prisustva po grupama.
     *
     * Grupe istog predmeta su posebni zapisi (npr. Г1-Г4 ili Л1-Л11 nakon automatske podjele prve
     * godine), pa se porede zapisi koji dijele sifru, tip nastave, akademsku godinu i vlasnika.
     * Ako je to jedini zapis na prvoj godini (npr. predavanja, nepodijeljen spisak), grupe se
     * odredjuju po pravilima za indekse unutar tog spiska.
     */
    private Map<String, Double> buildGroupStats(Subject subject, SubjectAnalyticsDTO dto) {
        List<Subject> siblings = subjectRepository.findByCode(subject.getCode()).stream()
                .filter(s -> s.getCreatedBy() != null
                        && s.getCreatedBy().getId().equals(subject.getCreatedBy().getId())
                        && Objects.equals(s.getTeachingType(), subject.getTeachingType())
                        && Objects.equals(s.getAcademicYear(), subject.getAcademicYear()))
                .toList();

        Map<String, Double> result = new TreeMap<>(AnalyticsController::compareGroupNames);

        boolean firstYear = subject.getStudyYear() != null && subject.getStudyYear() == 1;
        if (firstYear && siblings.size() <= 1) {
            result.putAll(dto.getGroupStats());
            return result;
        }

        for (Subject s : siblings) {
            SubjectAnalyticsDTO sDto = s.getId().equals(subject.getId())
                    ? dto
                    : analyticsService.getSubjectAnalytics(s.getId(), defaultThreshold);
            double average = sDto.getStudentStats().stream()
                    .mapToDouble(SubjectAnalyticsDTO.StudentStat::getPercentage)
                    .average()
                    .orElse(0.0);
            result.put(s.getGroupName() != null ? s.getGroupName() : "G-Nepoznato", average);
        }
        return result;
    }

    private static final Pattern GROUP_NAME = Pattern.compile("^(.*?)(\\d+)$");

    /** Prirodni redoslijed grupa: Г1, Г2, ..., Л2, Л10; nazivi bez broja (npr. "Ostali") idu na kraj. */
    private static int compareGroupNames(String a, String b) {
        Matcher ma = GROUP_NAME.matcher(a);
        Matcher mb = GROUP_NAME.matcher(b);
        boolean na = ma.matches();
        boolean nb = mb.matches();
        if (na && nb) {
            int c = ma.group(1).compareTo(mb.group(1));
            if (c != 0) return c;
            c = new BigInteger(ma.group(2)).compareTo(new BigInteger(mb.group(2)));
            return c != 0 ? c : a.compareTo(b);
        }
        if (na) return -1;
        if (nb) return 1;
        return a.compareTo(b);
    }
}
