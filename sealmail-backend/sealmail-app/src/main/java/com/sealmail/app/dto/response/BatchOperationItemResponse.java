package com.sealmail.app.dto.response;

public record BatchOperationItemResponse(
        String id,
        boolean success,
        String errorCode,
        String message
) {
    public static BatchOperationItemResponse success(String id) {
        return new BatchOperationItemResponse(id, true, null, null);
    }

    public static BatchOperationItemResponse failure(String id, String errorCode, String message) {
        return new BatchOperationItemResponse(id, false, errorCode, message);
    }
}
