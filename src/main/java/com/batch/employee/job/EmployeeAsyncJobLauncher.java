package com.batch.employee.job;

import com.batch.employee.repository.BatchImportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmployeeAsyncJobLauncher {

    private final EmployeeJobLauncher jobLauncher;
    private final BatchImportRepository batchImportRepository;

    @Async("batchTaskExecutor")
    public void launch(Long importId) {

        try {

            log.info("Starting batch job asynchronously. importId={}", importId);

            jobLauncher.launch(importId);

        } catch (Exception e) {
            log.error("Failed to launch batch job. importId={}", importId, e);
            batchImportRepository.markFailed(importId, e.getMessage());
        }
    }

}
