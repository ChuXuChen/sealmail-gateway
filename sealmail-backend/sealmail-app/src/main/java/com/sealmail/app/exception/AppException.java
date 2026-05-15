package com.sealmail.app.exception;

public abstract class AppException extends RuntimeException {

    private final String code;

    protected AppException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected AppException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
