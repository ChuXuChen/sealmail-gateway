package com.sealmail.app.exception;

public class SecurityException extends AppException {

    public SecurityException(String code, String message) {
        super(code, message);
    }

    public SecurityException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public static SecurityException accessDenied(String message) {
        return new SecurityException("ACCESS_DENIED", message);
    }

    public static SecurityException unauthorized(String message) {
        return new SecurityException("UNAUTHORIZED", message);
    }
}
