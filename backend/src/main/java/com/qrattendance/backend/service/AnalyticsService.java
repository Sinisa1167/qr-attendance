package com.qrattendance.backend.service;

import com.qrattendance.backend.dto.SubjectAnalyticsDTO;
import com.qrattendance.backend.model.*;
import com.qrattendance.backend.repository.AttendanceRepository;
import com.qrattendance.backend.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final SubjectService subjectService;

    public SubjectAnalyticsDTO getSubjectAnalytics(String subjectId, double threshold) {
        Subject subject = subjectService.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("Predmet nije pronađen"));

        // Uzimamo samo ZATVORENE sesije (one koje su završene)
        List<Session> closedSessions = sessionRepository.findBySubject(subject).stream()
                .filter(s -> s.getStatus() == Session.SessionStatus.CLOSED)
                .sorted(Comparator.comparing(Session::getDate))
                .collect(Collectors.toList());

        int totalSessionsCount = closedSessions.size();

        // 1. Statistika po sesijama (za Line Chart - Trend prisustva)
        List<SubjectAnalyticsDTO.SessionStat> sessionStats = closedSessions.stream()
                .map(s -> {
                    SubjectAnalyticsDTO.SessionStat stat = new SubjectAnalyticsDTO.SessionStat();
                    stat.setDate(s.getDate().toString());
                    stat.setActivityType(s.getActivityType().toString());
                    stat.setCount(attendanceRepository.findBySession(s).size());
                    return stat;
                })
                .collect(Collectors.toList());

        // 2. Statistika po studentima
        List<SubjectAnalyticsDTO.StudentStat> studentStats = subject.getStudents().stream()
                .map(student -> {
                    long attended = closedSessions.stream()
                            .filter(s -> attendanceRepository.existsBySessionAndStudent(s, student))
                            .count();

                    double percent = totalSessionsCount > 0 
                            ? Math.round((attended * 100.0 / totalSessionsCount) * 100.0) / 100.0 
                            : 0.0;

                    return SubjectAnalyticsDTO.StudentStat.builder()
                            .fullName(student.getFirstName() + " " + student.getLastName())
                            .index(student.getIndexNumber())
                            .attendedCount(attended)
                            .percentage(percent)
                            .belowThreshold(percent < threshold)
                            .build();
                })
                .sorted(Comparator.comparing(SubjectAnalyticsDTO.StudentStat::getPercentage).reversed()) // najbolji prvi
                .collect(Collectors.toList());

        // 3. Statistika po grupama (G1, G2, G3...) – bonus
        Map<String, Double> groupStats = calculateGroupStats(studentStats);

        return SubjectAnalyticsDTO.builder()
                .subjectName(subject.getName() + " (" + subject.getTeachingType() + ")")
                .totalSessions(totalSessionsCount)
                .overallAverageAttendance(studentStats.stream()
                        .mapToDouble(SubjectAnalyticsDTO.StudentStat::getPercentage)
                        .average()
                        .orElse(0.0))
                .sessionStats(sessionStats)
                .studentStats(studentStats)
                .groupStats(groupStats)
                .build();
    }

    private Map<String, Double> calculateGroupStats(List<SubjectAnalyticsDTO.StudentStat> studentStats) {
        return studentStats.stream()
                .collect(Collectors.groupingBy(
                        s -> determineGroupName(s.getIndex()),
                        Collectors.averagingDouble(SubjectAnalyticsDTO.StudentStat::getPercentage)
                ));
    }

    /**
     * Određuje grupu na osnovu broja indeksa (prema uobičajenim pravilima na fakultetu)
     */
    private String determineGroupName(String index) {
        if (index == null || !index.contains("/")) return "Ostali";
        
        try {
            int num = Integer.parseInt(index.split("/")[0]);
            
            if (num >= 1101 && num <= 1159) return "Г1";
            if (num >= 1160 && num <= 11117) return "Г2";
            if ((num >= 11118 && num <= 11123) || (num >= 1201 && num <= 1252)) return "Г3";
            if ((num >= 1253 && num <= 1255) || (num >= 1301 && num <= 1353)) return "Г4";
            
            return "Ostali";
        } catch (Exception e) {
            return "Nepoznat format";
        }
    }
}