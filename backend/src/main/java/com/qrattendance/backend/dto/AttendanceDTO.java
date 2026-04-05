package com.qrattendance.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttendanceDTO {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String indexNumber;
    private String checkInTime;
    private String ipAddress;
    private String userAgent;
    private String tokenUsed;
}