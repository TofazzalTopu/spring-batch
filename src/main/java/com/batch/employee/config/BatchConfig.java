package com.batch.employee.config;

import com.batch.employee.listener.JobCompletionListener;
import com.batch.employee.model.Employee;
import com.batch.employee.processor.EmployeeProcessor;
import java.sql.SQLException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

  @Bean
  public Job employeeJob(
          JobRepository jobRepository,
          Step employeeStep,
          JobCompletionListener listener) {

    return new JobBuilder("employeeJob", jobRepository)
            .listener(listener)
            .start(employeeStep)
            .build();
  }

  @Bean
  public Step employeeStep(
          JobRepository repository,
          PlatformTransactionManager txManager,
          FlatFileItemReader<Employee> reader,
          EmployeeProcessor processor,
          JdbcBatchItemWriter<Employee> writer) {

    return new StepBuilder("employeeStep", repository)
            .<Employee, Employee>chunk(100, txManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .retry(SQLException.class)
            .retryLimit(3)
            .skip(NumberFormatException.class)
            .skipLimit(100)
            .build();
  }
}