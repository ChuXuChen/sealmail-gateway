package com.sealmail.web.exception;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.exception.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException e, WebRequest request) {

        log.warn("Business exception: {}", e.getMessage());

        HttpStatus status = statusForBusinessException(e);
        ApiErrorResponse error = ApiErrorResponse.builder()
                .success(false)
                .code(status.value())
                .errorCode(e.getCode())
                .message(e.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException e, WebRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(),
                    fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value");
        }

        ApiErrorResponse error = ApiErrorResponse.builder()
                .success(false)
                .code(HttpStatus.BAD_REQUEST.value())
                .errorCode("VALIDATION_ERROR")
                .message("参数校验失败")
                .path(request.getDescription(false).replace("uri=", ""))
                .fieldErrors(fieldErrors)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Spring 6+ raises this when {@code @Min/@Max/@Pattern} on {@code @RequestParam}
     * primitives fail (vs. @Valid on @RequestBody which uses MethodArgumentNotValid).
     * Without this handler it falls into the generic {@code Exception} catcher and
     * returns 500, masking what's really a client-side 400.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException e, WebRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .success(false)
                .code(HttpStatus.BAD_REQUEST.value())
                .errorCode("VALIDATION_ERROR")
                .message("请求参数校验失败: " + e.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException e, WebRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .success(false)
                .code(HttpStatus.BAD_REQUEST.value())
                .errorCode("BAD_REQUEST")
                .message(e.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException e, WebRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .success(false)
                .code(HttpStatus.NOT_FOUND.value())
                .errorCode("NOT_FOUND")
                .message(e.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(
            UnauthorizedException e, WebRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .success(false)
                .code(HttpStatus.UNAUTHORIZED.value())
                .errorCode("UNAUTHORIZED")
                .message(e.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException e, WebRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .success(false)
                .code(HttpStatus.FORBIDDEN.value())
                .errorCode("FORBIDDEN")
                .message("没有权限执行此操作")
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponse> handleSecurityException(
            SecurityException e, WebRequest request) {

        HttpStatus status = "UNAUTHORIZED".equals(e.getCode())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.FORBIDDEN;
        ApiErrorResponse error = ApiErrorResponse.builder()
                .success(false)
                .code(status.value())
                .errorCode(e.getCode())
                .message(e.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAllUncaughtException(
            Exception e, WebRequest request) {

        log.error("Uncaught exception occurred", e);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .success(false)
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorCode("INTERNAL_ERROR")
                .message("系统内部错误，请联系管理员")
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private HttpStatus statusForBusinessException(BusinessException e) {
        return switch (e.getCode()) {
            case "BAD_REQUEST" -> HttpStatus.BAD_REQUEST;
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.CONFLICT;
        };
    }
}
