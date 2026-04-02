package com.qrattendance.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QrToken implements Serializable {

    private String token;

    private String sessionId;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private boolean used;
}