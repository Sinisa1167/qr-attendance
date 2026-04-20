package com.qrattendance.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SubjectAnalyticsDTO {
    private String subjectName;
    private int totalSessions;
    private double overallAverageAttendance;
    private List<SessionStat> sessionStats;
    private List<StudentStat> studentStats;
    private Map<String, Double> groupStats;

    public double getAveragePercentage() {
        if (studentStats == null || studentStats.isEmpty()) return 0.0;
        return studentStats.stream()
                .mapToDouble(StudentStat::getPercentage)
                .average()
                .orElse(0.0);
    }

    @Data
    public static class SessionStat {
        private String date;
        private String activityType;
        private long count;
    }

    @Data
    @Builder
    public static class StudentStat {
        private String fullName;
        private String index;
        private long attendedCount;
        private double percentage;
        private boolean belowThreshold;
    }
}