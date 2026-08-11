package com.batch.employee.reader;

import com.batch.employee.model.EmployeeStaging;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class EmployeeStagingReaderConfig {

    @Bean
    @StepScope
    public JdbcCursorItemReader<EmployeeStaging> employeeStagingReader (
            DataSource dataSource,
            @Value("#{jobParameters['importId']}") Long importId) {

        return new JdbcCursorItemReaderBuilder<EmployeeStaging>()
                .name("employeeStagingReader")
                .dataSource(dataSource)
                .sql("""
                    SELECT
                        staging_id,
                        import_id,
                        source_row_number,
                        name,
                        email,
                        salary,
                        validation_status,
                        error_message
                    FROM employee_staging
                    WHERE import_id = ?
                      AND validation_status = 'VALID'
                    ORDER BY source_row_number
                    """)
                .preparedStatementSetter(
                        ps -> ps.setLong(1, importId))
                .rowMapper((rs, rowNum) -> {

                    EmployeeStaging staging = new EmployeeStaging();

                    staging.setImportId(rs.getLong("import_id"));
                    staging.setRowNumber(rs.getLong("source_row_number"));
                    staging.setName(rs.getString("name"));
                    staging.setEmail(rs.getString("email"));
                    staging.setSalary(rs.getDouble("salary"));
                    staging.setValidationStatus(
                            rs.getString("validation_status"));
                    staging.setErrorMessage(
                            rs.getString("error_message"));

                    return staging;
                })
                .build();
    }
}