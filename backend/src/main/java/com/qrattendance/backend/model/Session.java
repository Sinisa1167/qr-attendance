package com.qrattendance.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @JsonIgnoreProperties
    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activityType;

    @JsonFormat(pattern = "yyyy-MM-dd", shape = JsonFormat.Shape.STRING)
    @Column(nullable = false)
    private LocalDate date;

    @JsonFormat(pattern = "HH:mm")
    @Column(nullable = false)
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    @Column(nullable = false)
    private LocalTime endTime;

    private String groupNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @JsonFormat(pattern = "dd.MM.yyyy")
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "dd.MM.yyyy")
    private LocalDateTime activatedAt;

    @JsonFormat(pattern = "dd.MM.yyyy")
    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = SessionStatus.CREATED;
        }
    }

    @JsonIgnore
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attendance> attendanceList;

    public enum ActivityType {
        PREDAVANJE, AUDITORNE_VJEZBE, LABORATORIJSKE_VJEZBE
    }

    public enum SessionStatus {
        CREATED, ACTIVE, CLOSED
    }
}