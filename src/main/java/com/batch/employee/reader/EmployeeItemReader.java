package com.batch.employee.reader;

import com.batch.employee.model.Employee;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class EmployeeItemReader {

  @Bean
  public FlatFileItemReader<Employee> reader() {

    BeanWrapperFieldSetMapper<Employee> fieldSetMapper = new BeanWrapperFieldSetMapper<>();

    fieldSetMapper.setTargetType(Employee.class);

    return new FlatFileItemReaderBuilder<Employee>()
        .name("employeeReader")
        .resource(new ClassPathResource("employees.csv"))
        .linesToSkip(1)
        .delimited()
        .names("id", "name", "email", "salary")
        .fieldSetMapper(fieldSetMapper)
        .build();
  }
}
