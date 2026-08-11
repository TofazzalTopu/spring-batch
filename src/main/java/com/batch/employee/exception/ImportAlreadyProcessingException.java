package com.batch.employee.exception;

public class ImportAlreadyProcessingException
        extends RuntimeException {

    public ImportAlreadyProcessingException(String message) {
        super(message);
    }
}