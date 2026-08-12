package com.batch.employee.tasklet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizeImportTasklet implements Tasklet {

    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext) {

        Long importId = Long.valueOf(
                chunkContext.getStepContext()
                        .getJobParameters()
                        .get("importId")
                        .toString()
        );

        log.info("Import completed successfully. importId={}", importId);

        return RepeatStatus.FINISHED;
    }
}