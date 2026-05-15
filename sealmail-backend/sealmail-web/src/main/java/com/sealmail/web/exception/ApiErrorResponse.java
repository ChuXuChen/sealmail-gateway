package com.sealmail.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private boolean success;
    private int code;
    private String errorCode;
    private String message;
    private String path;
    private Instant timestamp;
    private Map<String, String> fieldErrors;

    public static ApiErrorResponse notFound(String message) {
        return ApiErrorResponse.builder()
                .success(false)
                .code(404)
                .errorCode("NOT_FOUND")
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static ApiErrorResponse internalError(String message, String path) {
        return ApiErrorResponse.builder()
                .success(false)
                .code(500)
                .errorCode("INTERNAL_ERROR")
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();
    }
}
