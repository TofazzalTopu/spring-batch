package com.batch.employee.processor;

import com.batch.employee.model.Employee;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class EmployeeProcessor implements ItemProcessor<Employee, Employee> {

  @Override
  public Employee process(Employee employee) {

    employee.setName(employee.getName().toUpperCase());

    employee.setSalary(employee.getSalary() * 1.10);

    return employee;
  }
}
