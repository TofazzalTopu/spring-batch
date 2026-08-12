package com.batch.employee.scheduler;

import com.batch.employee.job.EmployeeJobLauncher;
import com.batch.employee.repository.BatchImportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmployeeImportScheduler {

    private final BatchImportRepository batchImportRepository;
    private final EmployeeJobLauncher jobLauncher;

    @Scheduled(fixedDelayString = "${app.import.scheduler.delay:30000}")
    public void processPendingImports() {

        var imports = batchImportRepository.findReceivedImports(5);

        log.info("Starting scheduler. No of records to process: {}", (long) imports.size());

        for (Long importId : imports) {
            try {
                /*
                 * Important:
                 * Atomically claim the import.
                 */
                boolean claimed = batchImportRepository.markProcessing(importId);

                if (!claimed) {
                    log.info("Import {} was already claimed", importId);
                    continue;
                }

                log.info("Starting import {}", importId);
                jobLauncher.launch(importId);
            } catch (Exception e) {
                log.error("Import {} failed", importId, e);
                batchImportRepository.markFailed(importId, e.getMessage());
            }
        }
    }

}