package com.sealmail.web.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private int code;
    private String message;
    private T data;
    private Instant timestamp;
    private String requestId;

    public static <T> ApiResponse<T> ok() {
        return ApiResponse.<T>builder()
                .success(true)
                .code(200)
                .message("操作成功")
                .timestamp(Instant.now())
                .requestId(generateRequestId())
                .build();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(200)
                .message("操作成功")
                .data(data)
                .timestamp(Instant.now())
                .requestId(generateRequestId())
                .build();
    }

    public static <T> ApiResponse<T> created(T data, String location) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(201)
                .message("创建成功")
                .data(data)
                .timestamp(Instant.now())
                .requestId(generateRequestId())
                .build();
    }

    public static <T> ApiResponse<T> noContent() {
        return ApiResponse.<T>builder()
                .success(true)
                .code(204)
                .message("删除成功")
                .timestamp(Instant.now())
                .requestId(generateRequestId())
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .requestId(generateRequestId())
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .requestId(generateRequestId())
                .build();
    }

    private static String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
