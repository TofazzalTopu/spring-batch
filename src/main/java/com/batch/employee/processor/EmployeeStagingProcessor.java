package com.batch.employee.processor;

import com.batch.employee.model.Employee;
import com.batch.employee.model.EmployeeStaging;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class EmployeeStagingProcessor implements ItemProcessor<Employee, EmployeeStaging> {

    private final Long importId;

    public EmployeeStagingProcessor(
            @Value("#{jobParameters['importId']}") Long importId) {

        this.importId = importId;
    }

    private long rowNumber = 0;

    @Override
    public EmployeeStaging process(Employee employee) {

        rowNumber++;

        if (employee.getName() != null) {
            employee.setName(employee.getName().trim());
        }

        if (employee.getEmail() != null) {
            employee.setEmail(employee.getEmail().trim().toLowerCase());
        }

        EmployeeStaging staging = new EmployeeStaging();

        staging.setImportId(importId);
        staging.setRowNumber(rowNumber);
        staging.setName(employee.getName());
        staging.setEmail(employee.getEmail());
        staging.setSalary(employee.getSalary());
        staging.setValidationStatus("VALID");

        return staging;
    }
}