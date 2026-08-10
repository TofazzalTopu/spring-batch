package com.batch.employee.writer;

import com.batch.employee.model.Employee;
import javax.sql.DataSource;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmployeeItemWriter {

  @Bean
  public JdbcBatchItemWriter<Employee> writer(DataSource dataSource) {

    JdbcBatchItemWriter<Employee> writer = new JdbcBatchItemWriter<>();

    writer.setDataSource(dataSource);

    writer.setSql(
        """
            INSERT INTO employee(id,name,email,salary)
            VALUES(:id,:name,:email,:salary)
        """);

    writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());

    writer.afterPropertiesSet();
    return writer;
  }
}
