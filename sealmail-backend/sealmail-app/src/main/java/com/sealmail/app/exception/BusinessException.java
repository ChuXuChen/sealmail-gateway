package com.sealmail.app.exception;

public class BusinessException extends AppException {

    public BusinessException(String code, String message) {
        super(code, message);
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException("FORBIDDEN", message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException("BAD_REQUEST", message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException("CONFLICT", message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException("UNAUTHORIZED", message);
    }
}
