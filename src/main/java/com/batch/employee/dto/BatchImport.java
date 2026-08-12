package com.batch.employee.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BatchImport {

    private Long importId;

    private String fileName;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private String errorMessage;

    private Long jobExecutionId;
}