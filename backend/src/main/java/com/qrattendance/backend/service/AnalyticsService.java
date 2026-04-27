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

        List<Session> closedSessions = sessionRepository.findBySubject(subject).stream()
                .filter(s -> s.getStatus() == Session.SessionStatus.CLOSED)
                .sorted(Comparator.comparing(Session::getDate))
                .collect(Collectors.toList());

        int totalSessionsCount = closedSessions.size();

        Map<String, Set<String>> sessionAttendedStudentIds = new HashMap<>();
        for (Session s : closedSessions) {
            Set<String> studentIds = attendanceRepository.findBySession(s).stream()
                    .map(a -> a.getStudent().getId())
                    .collect(Collectors.toSet());
            sessionAttendedStudentIds.put(s.getId(), studentIds);
        }

        List<SubjectAnalyticsDTO.SessionStat> sessionStats = closedSessions.stream()
                .map(s -> {
                    SubjectAnalyticsDTO.SessionStat stat = new SubjectAnalyticsDTO.SessionStat();
                    stat.setDate(s.getDate().toString());
                    stat.setActivityType(s.getActivityType().toString());
                    stat.setCount(sessionAttendedStudentIds.get(s.getId()).size());
                    return stat;
                })
                .collect(Collectors.toList());

        List<SubjectAnalyticsDTO.SessionInfo> sessionHeaders = closedSessions.stream()
                .map(s -> SubjectAnalyticsDTO.SessionInfo.builder()
                        .sessionId(s.getId())
                        .date(s.getDate().toString())
                        .activityType(s.getActivityType().toString())
                        .build())
                .collect(Collectors.toList());

        List<SubjectAnalyticsDTO.StudentStat> studentStats = subject.getStudents().stream()
                .map(student -> {
                    Map<String, Boolean> sessionAttendance = new LinkedHashMap<>();
                    long attended = 0;
                    for (Session s : closedSessions) {
                        boolean wasPresent = sessionAttendedStudentIds.get(s.getId()).contains(student.getId());
                        sessionAttendance.put(s.getId(), wasPresent);
                        if (wasPresent) attended++;
                    }

                    double percent = totalSessionsCount > 0
                            ? Math.round((attended * 100.0 / totalSessionsCount) * 100.0) / 100.0
                            : 0.0;

                    return SubjectAnalyticsDTO.StudentStat.builder()
                            .fullName(student.getFirstName() + " " + student.getLastName())
                            .index(student.getIndexNumber())
                            .attendedCount(attended)
                            .percentage(percent)
                            .belowThreshold(percent < threshold)
                            .sessionAttendance(sessionAttendance)
                            .build();
                })
                .sorted(Comparator.comparing(SubjectAnalyticsDTO.StudentStat::getPercentage).reversed())
                .collect(Collectors.toList());

        Map<String, Double> groupStats = calculateGroupStats(studentStats);

        return SubjectAnalyticsDTO.builder()
                .subjectName(subject.getName() + " (" + subject.getTeachingType() + ")")
                .totalSessions(totalSessionsCount)
                .overallAverageAttendance(studentStats.stream()
                        .mapToDouble(SubjectAnalyticsDTO.StudentStat::getPercentage)
                        .average()
                        .orElse(0.0))
                .sessionStats(sessionStats)
                .sessionHeaders(sessionHeaders)
                .studentStats(studentStats)
                .groupStats(groupStats)
                .build();
    }

    private Map<String, Double> calculateGroupStats(List<SubjectAnalyticsDTO.StudentStat> studentStats) {
        return studentStats.stream()
                .collect(Collectors.groupingBy(
                        s -> determineAuditoryGroup(s.getIndex()),
                        Collectors.averagingDouble(SubjectAnalyticsDTO.StudentStat::getPercentage)
                ));
    }

    public static String determineAuditoryGroup(String index) {
        if (index == null || !index.contains("/")) return "Ostali";
        try {
            int num = Integer.parseInt(index.split("/")[0]);
            if (num >= 1101 && num <= 1159)   return "Г1";
            if (num >= 1160 && num <= 11117)  return "Г2";
            if ((num >= 11118 && num <= 11123) || (num >= 1201 && num <= 1252)) return "Г3";
            if ((num >= 1253 && num <= 1255)  || (num >= 1301 && num <= 1353)) return "Г4";
            return "Ostali";
        } catch (Exception e) {
            return "Nepoznat format";
        }
    }

    public static String determineLaboratoryGroup(String index) {
        if (index == null || !index.contains("/")) return "Ostali";
        try {
            int num = Integer.parseInt(index.split("/")[0]);
            if (num >= 1101  && num <= 1121)  return "Л1";
            if (num >= 1122  && num <= 1144)  return "Л2";
            if (num >= 1145  && num <= 1165)  return "Л3";
            if (num >= 1166  && num <= 1188)  return "Л4";
            if (num >= 1189  && num <= 11108) return "Л5";
            if ((num >= 11109 && num <= 11123) || (num >= 1201 && num <= 1206)) return "Л6";
            if (num >= 1207  && num <= 1226)  return "Л7";
            if (num >= 1227  && num <= 1249)  return "Л8";
            if ((num >= 1251 && num <= 1255)  || (num >= 1301 && num <= 1316)) return "Л9";
            if (num >= 1317  && num <= 1337)  return "Л10";
            if (num >= 1338  && num <= 1353)  return "Л11";
            return "Ostali";
        } catch (Exception e) {
            return "Nepoznat format";
        }
    }
}