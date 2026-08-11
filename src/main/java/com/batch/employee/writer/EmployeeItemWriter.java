package com.batch.employee.writer;

import com.batch.employee.model.EmployeeStaging;
import javax.sql.DataSource;

import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmployeeItemWriter {

  @Bean
  public JdbcBatchItemWriter<EmployeeStaging> employeeStagingWriter(
          DataSource dataSource) {

    return new JdbcBatchItemWriterBuilder<EmployeeStaging>()
            .dataSource(dataSource)
            .sql("""
                    INSERT INTO employee_staging
                        (
                            import_id,
                            source_row_number,
                            name,
                            email,
                            salary,
                            validation_status
                        )
                    VALUES
                        (
                            :importId,
                            :rowNumber,
                            :name,
                            :email,
                            :salary,
                            :validationStatus
                        )
                    """)
            .beanMapped()
            .build();
  }

  @Bean
  public JdbcBatchItemWriter<EmployeeStaging> employeeLoadWriter(
          DataSource dataSource) {

    return new JdbcBatchItemWriterBuilder<EmployeeStaging>()
            .dataSource(dataSource)
            .sql("""
                    INSERT INTO employee
                        (
                            name,
                            email,
                            salary
                        )
                    VALUES
                        (
                            :name,
                            :email,
                            :salary
                        )
                    """)
            .beanMapped()
            .build();
  }
}