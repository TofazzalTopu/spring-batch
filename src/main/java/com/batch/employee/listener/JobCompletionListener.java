package com.batch.employee.listener;

import com.batch.employee.repository.BatchImportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobCompletionListener implements JobExecutionListener {

  private final BatchImportRepository batchImportRepository;

  @Override
  public void beforeJob(JobExecution jobExecution) {

    Long importId = jobExecution.getJobParameters().getLong("importId");

    log.info("Batch job started. importId={}, executionId={}", importId, jobExecution.getId());
  }

  @Override
  public void afterJob(JobExecution jobExecution) {

    Long importId = jobExecution.getJobParameters().getLong("importId");

    BatchStatus status = jobExecution.getStatus();

    log.info("Batch job finished. importId={}, executionId={}, status={}", importId, jobExecution.getId(), status);

    if (status == BatchStatus.COMPLETED) {

      batchImportRepository.markCompleted(importId);

    } else if (status == BatchStatus.FAILED) {

      String errorMessage = jobExecution.getAllFailureExceptions()
                      .stream()
                      .map(Throwable::getMessage)
                      .filter(Objects::nonNull)
                      .findFirst()
                      .orElse("Batch job failed");

      batchImportRepository.markFailed(importId, errorMessage);
    }
  }

}