package com.batch.employee.reader;

import com.batch.employee.model.Employee;
import com.batch.employee.repository.BatchImportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Path;

@Slf4j
@Configuration
public class EmployeeItemReaderConfig {

  @Bean
  @StepScope
  public FlatFileItemReader<Employee> employeeReader (
          BatchImportRepository batchImportRepository,
          @Value("${app.import.input-directory}") String inputDirectory,
          @Value("#{jobParameters['importId']}") Long importId) {

    String fileName = batchImportRepository.findFileName(importId);

    Path filePath = Path.of(inputDirectory, fileName);

    log.info("Import ID: {}", importId);

    log.info("Reading file: {}", filePath);

    BeanWrapperFieldSetMapper<Employee> fieldSetMapper = new BeanWrapperFieldSetMapper<>();

    fieldSetMapper.setTargetType(Employee.class);

    return new FlatFileItemReaderBuilder<Employee>()
            .name("employeeReader")
            .resource(new FileSystemResource(filePath))
            .linesToSkip(1)
            .delimited()
            .names("name", "email", "salary")
            .fieldSetMapper(fieldSetMapper)
            .build();
  }

}