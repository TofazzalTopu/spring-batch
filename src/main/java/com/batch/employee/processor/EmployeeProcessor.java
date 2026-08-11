package com.batch.employee.processor;

import com.batch.employee.model.Employee;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class EmployeeProcessor implements ItemProcessor<Employee, Employee> {

  @Override
  public Employee process(Employee employee) {

    if (employee.getName() != null) {
      employee.setName(employee.getName().trim());
    }

    if (employee.getEmail() != null) {
      employee.setEmail(employee.getEmail().trim().toLowerCase());
    }

    return employee;
  }
}
