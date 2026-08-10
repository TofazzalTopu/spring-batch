package com.batch.employee.listener;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class JobCompletionListener implements JobExecutionListener {

  @Override
  public void beforeJob(JobExecution jobExecution) {
    System.out.println("Job started: " + jobExecution.getJobInstance().getJobName());
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    System.out.println("Job completed with status: " + jobExecution.getStatus());
  }
}
