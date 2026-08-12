package com.batch.employee.controller;

import com.batch.employee.dto.ImportStatusResponse;
import com.batch.employee.exception.ImportAlreadyCompletedException;
import com.batch.employee.exception.ImportAlreadyProcessingException;
import com.batch.employee.service.EmployeeImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static com.batch.employee.constants.AppConstant.*;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class EmployeeImportController {

    private final EmployeeImportService employeeImportService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws Exception {

        Long importId = employeeImportService.uploadAndStart(file);

        return ResponseEntity.accepted().body(
                Map.of(
                        IMPORTID, importId,
                        STATUS, PROCESSING
                )
        );
    }

    @PostMapping("/execute/{importId}")
    public ResponseEntity<Map<String, Object>> executeJobByImportId(@PathVariable Long importId) {

        try {
            employeeImportService.executeJobByImportId(importId);

            return ResponseEntity
                    .accepted()
                    .body(
                            Map.of(
                                    IMPORTID, importId,
                                    STATUS, PROCESSING
                            )
                    );

        } catch (ImportAlreadyCompletedException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                            Map.of(
                                    IMPORTID, importId,
                                    STATUS, BatchStatus.COMPLETED,
                                    "message", e.getMessage()
                            )
                    );

        } catch (ImportAlreadyProcessingException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                            Map.of(
                                    IMPORTID, importId,
                                    STATUS, PROCESSING,
                                    "message", e.getMessage()
                            )
                    );
        }
    }

    @GetMapping("/{importId}")
    public ResponseEntity<ImportStatusResponse> getImportStatus(@PathVariable Long importId) {

        return ResponseEntity.ok(employeeImportService.getImportStatus(importId));
    }

}