package com.batch.employee.exception;

public class ImportAlreadyCompletedException
        extends RuntimeException {

    public ImportAlreadyCompletedException(String message) {
        super(message);
    }
}