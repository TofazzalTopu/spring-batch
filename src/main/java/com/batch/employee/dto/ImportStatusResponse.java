package com.batch.employee.dto;

import java.time.LocalDateTime;

public record ImportStatusResponse(
        Long importId,
        String fileName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage,

        Long readCount,
        Long writeCount,
        Long filterCount,
        Long skipCount,
        Long commitCount,
        Long rollbackCount
) {
}