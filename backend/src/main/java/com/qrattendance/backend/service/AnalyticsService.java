package com.qrattendance.backend.service;

import com.qrattendance.backend.config.GroupRulesConfig;
import com.qrattendance.backend.dto.SubjectAnalyticsDTO;
import com.qrattendance.backend.model.*;
import com.qrattendance.backend.repository.AttendanceRepository;
import com.qrattendance.backend.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.NoSuchElementException;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final SubjectService subjectService;
    private final ExcelParserService excelParserService;

    @Transactional(readOnly = true)
	public SubjectAnalyticsDTO getSubjectAnalytics(String subjectId, double threshold) {
                Subject subject = subjectService.findById(subjectId)
                .orElseThrow(() -> new NoSuchElementException("Predmet nije pronađen"));

        List<Session> closedSessions = sessionRepository.findBySubjectOrderByCreatedAtDesc(subject).stream()
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
                    stat.setCount(sessionAttendedStudentIds.getOrDefault(s.getId(), Collections.emptySet()).size());
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
                        boolean wasPresent = sessionAttendedStudentIds
                                .getOrDefault(s.getId(), Collections.emptySet())
                                .contains(student.getId());
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

        Map<String, Double> groupStats = calculateGroupStats(studentStats, subject.getTeachingType());

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

    private Map<String, Double> calculateGroupStats(
            List<SubjectAnalyticsDTO.StudentStat> studentStats, String teachingType) {

        List<GroupRulesConfig.GroupDef> rules = excelParserService.isLaboratorijske(teachingType)
                ? excelParserService.getLaboratorijskeRules()
                : excelParserService.getAuditorneRules();

        return studentStats.stream()
                .collect(Collectors.groupingBy(
                        s -> excelParserService.resolveGroupForIndex(s.getIndex(), rules),
                        Collectors.averagingDouble(SubjectAnalyticsDTO.StudentStat::getPercentage)
                ));
    }
}