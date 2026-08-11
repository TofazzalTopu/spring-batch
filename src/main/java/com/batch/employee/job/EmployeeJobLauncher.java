package com.batch.employee.job;

import com.batch.employee.repository.BatchImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmployeeJobLauncher {

    private final JobLauncher jobLauncher;
    private final Job employeeImportJob;
    private final BatchImportRepository batchImportRepository;

    public JobExecution launch(Long importId) throws Exception {

        JobParameters parameters =
                new JobParametersBuilder()
                        .addLong("importId", importId)
                        .toJobParameters();

        JobExecution execution = jobLauncher.run(employeeImportJob, parameters);

        batchImportRepository.updateJobExecutionId(importId, execution.getId());

        return execution;
    }

}
