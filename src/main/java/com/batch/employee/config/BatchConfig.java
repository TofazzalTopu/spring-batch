package com.batch.employee.config;

import com.batch.employee.listener.JobCompletionListener;
import com.batch.employee.model.Employee;
import com.batch.employee.model.EmployeeStaging;
import com.batch.employee.processor.EmployeeStagingProcessor;
import com.batch.employee.tasklet.FinalizeImportTasklet;
import com.batch.employee.tasklet.PrepareImportTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

  @Bean
  public Job employeeImportJob(
          JobRepository jobRepository,
          Step prepareImportStep,
          Step employeeStagingStep,
          Step employeeLoadStep,
          Step finalizeImportStep,
          JobCompletionListener listener) {

    return new JobBuilder("employeeImportJob", jobRepository)
            .listener(listener)
            .start(prepareImportStep)
            .next(employeeStagingStep)
            .next(employeeLoadStep)
            .next(finalizeImportStep)
            .build();
  }

  @Bean
  public Step employeeStagingStep(
          JobRepository jobRepository,
          PlatformTransactionManager transactionManager,
          FlatFileItemReader<Employee> employeeReader,
          EmployeeStagingProcessor processor,
          JdbcBatchItemWriter<EmployeeStaging> employeeStagingWriter) {

    return new StepBuilder("employeeStagingStep", jobRepository)
            .<Employee, EmployeeStaging>chunk(1000, transactionManager)
            .reader(employeeReader)
            .processor(processor)
            .writer(employeeStagingWriter)
            .build();
  }

  @Bean
  public Step employeeLoadStep(
          JobRepository jobRepository,
          PlatformTransactionManager transactionManager,
          JdbcCursorItemReader<EmployeeStaging> employeeStagingReader,
          JdbcBatchItemWriter<EmployeeStaging> employeeLoadWriter) {

    return new StepBuilder("employeeLoadStep", jobRepository)
            .<EmployeeStaging, EmployeeStaging>chunk(1000, transactionManager)
            .reader(employeeStagingReader)
            .writer(employeeLoadWriter)
            .build();
  }


  @Bean
  public Step prepareImportStep(
          JobRepository jobRepository,
          PlatformTransactionManager transactionManager,
          PrepareImportTasklet prepareImportTasklet) {

    return new StepBuilder("prepareImportStep", jobRepository)
            .tasklet(prepareImportTasklet, transactionManager)
            .build();
  }
  @Bean
  public Step finalizeImportStep(
          JobRepository jobRepository,
          PlatformTransactionManager transactionManager,
          FinalizeImportTasklet finalizeImportTasklet) {

    return new StepBuilder("finalizeImportStep", jobRepository)
            .tasklet(finalizeImportTasklet, transactionManager)
            .build();
  }

}