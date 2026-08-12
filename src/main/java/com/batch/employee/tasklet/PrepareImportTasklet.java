package com.batch.employee.tasklet;

import com.batch.employee.dto.BatchImport;
import com.batch.employee.repository.BatchImportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class PrepareImportTasklet implements Tasklet {

    private final BatchImportRepository batchImportRepository;
    private final String inputDirectory;

    public PrepareImportTasklet(BatchImportRepository batchImportRepository, @Value("${app.import.input-directory}") String inputDirectory) {
        this.batchImportRepository = batchImportRepository;
        this.inputDirectory = inputDirectory;
    }

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

        log.info("Preparing import. importId={}", importId);

        BatchImport batchImport =
                batchImportRepository.findById(importId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Import not found: " + importId));

        // Validate file
        Path file = Path.of(inputDirectory + batchImport.getFileName());

        if (!Files.exists(file)) {
            throw new IllegalStateException("Import file does not exist: " + file);
        }

        log.info("Import prepared successfully. importId={}, file={}", importId, file);

        return RepeatStatus.FINISHED;
    }
}