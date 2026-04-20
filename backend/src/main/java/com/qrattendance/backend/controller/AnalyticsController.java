package com.qrattendance.backend.controller;

import com.qrattendance.backend.model.*;
import com.qrattendance.backend.repository.SubjectRepository;
import com.qrattendance.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SubjectService subjectService;
    private final SubjectRepository subjectRepository;

    @Value("${app.attendance.threshold:70.0}")
    private double defaultThreshold;

    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<?> getSubjectAnalytics(@PathVariable String subjectId) {
        Subject subject = subjectService.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("Predmet nije pronađen"));

        var dto = analyticsService.getSubjectAnalytics(subjectId, defaultThreshold);

        Map<String, Object> response = new HashMap<>();
        response.put("subjectName", dto.getSubjectName());
        response.put("groupName", subject.getGroupName());
        response.put("totalSessions", dto.getTotalSessions());
        
        response.put("studentStats", dto.getStudentStats().stream().map(stat -> {
            Map<String, Object> map = new HashMap<>();
            map.put("index", stat.getIndex());
            map.put("firstName", stat.getFullName().split(" ")[0]);
            map.put("lastName", stat.getFullName().contains(" ") ? stat.getFullName().split(" ", 2)[1] : "");
            map.put("attended", stat.getAttendedCount());
            map.put("percentage", stat.getPercentage());
            map.put("isCritical", stat.isBelowThreshold());
            return map;
        }).collect(Collectors.toList()));

        response.put("chartData", dto.getSessionStats().stream().map(s -> {
            Map<String, Object> point = new HashMap<>();
            point.put("name", "Sesija " + s.getDate());
            point.put("prisutni", s.getCount());
            return point;
        }).collect(Collectors.toList()));

        List<Subject> allGroups = subjectRepository.findByCode(subject.getCode());
        Map<String, Double> groupStats = new HashMap<>();
        
        for (Subject s : allGroups) {
            var sDto = analyticsService.getSubjectAnalytics(s.getId(), defaultThreshold);
            
            double average = sDto.getStudentStats().stream()
                    .mapToDouble(stat -> stat.getPercentage())
                    .average()
                    .orElse(0.0);
            
            groupStats.put(s.getGroupName() != null ? s.getGroupName() : "G-Nepoznato", average);
        }
        
        response.put("groupStats", groupStats);

        return ResponseEntity.ok(response);
    }
}