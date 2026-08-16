package com.jobfit.resumeparsing;

public class UnparsableFileException extends RuntimeException {
    public UnparsableFileException(String message) {
        super(message);
    }

    public UnparsableFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
