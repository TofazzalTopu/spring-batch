package com.batch.employee.service;

import com.batch.employee.dto.ImportStatusResponse;
import com.batch.employee.exception.ImportAlreadyCompletedException;
import com.batch.employee.exception.ImportAlreadyProcessingException;
import com.batch.employee.job.EmployeeAsyncJobLauncher;
import com.batch.employee.job.EmployeeJobLauncher;
import com.batch.employee.repository.BatchImportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class EmployeeImportService {

    private final BatchImportRepository batchImportRepository;
    private final EmployeeJobLauncher jobLauncher;

    private final EmployeeAsyncJobLauncher asyncJobLauncher;
    private final Path importDirectory;

    public EmployeeImportService(BatchImportRepository batchImportRepository, EmployeeJobLauncher jobLauncher, EmployeeAsyncJobLauncher asyncJobLauncher, @Value("${app.import.input-directory}") String inputDirectory) {
        this.batchImportRepository = batchImportRepository;
        this.jobLauncher = jobLauncher;
        this.asyncJobLauncher = asyncJobLauncher;
        this.importDirectory = Path.of(inputDirectory);
    }


    public Long uploadAndStart(MultipartFile file) throws Exception {

        validateFile(file);

        Files.createDirectories(importDirectory);

        String originalFileName = Path.of(file.getOriginalFilename()).getFileName().toString();

        String storedFileName = UUID.randomUUID() + "_" + originalFileName;

        Path target = importDirectory.resolve(storedFileName);

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        Long importId = batchImportRepository.createImport(storedFileName);

        try {

            if (!batchImportRepository.markProcessing(importId)) {
                throw new IllegalStateException("Unable to claim import " + importId);
            }

            jobLauncher.launch(importId);

        } catch (Exception e) {
            batchImportRepository.markFailed(importId, e.getMessage());
            throw e;
        }

        return importId;
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty");
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Only CSV files are supported");
        }
    }


    public void executeJobByImportId(Long importId) {

        log.info("Request received to execute importId={}", importId);

        boolean claimed = batchImportRepository.markProcessing(importId);

        if (!claimed) {

            String status = batchImportRepository.findStatus(importId);

            if ("COMPLETED".equals(status)) {
                throw new ImportAlreadyCompletedException("Import " + importId + " has already been completed");
            }

            if ("PROCESSING".equals(status)) {
                throw new ImportAlreadyProcessingException("Import " + importId + " is already being processed");
            }

            throw new IllegalStateException("Unable to claim import " + importId + ". Current status: " + status);
        }

        asyncJobLauncher.launch(importId);
    }


    public ImportStatusResponse getImportStatus(Long importId) {

        return batchImportRepository.findImportStatus(importId);
    }

}