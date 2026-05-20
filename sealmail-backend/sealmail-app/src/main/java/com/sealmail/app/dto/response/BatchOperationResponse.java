package com.sealmail.app.dto.response;

import java.util.List;

public record BatchOperationResponse(
        int requestedCount,
        int successCount,
        int failureCount,
        List<BatchOperationItemResponse> items
) {
    public static BatchOperationResponse of(List<BatchOperationItemResponse> items) {
        int successCount = (int) items.stream()
                .filter(BatchOperationItemResponse::success)
                .count();
        return new BatchOperationResponse(
                items.size(),
                successCount,
                items.size() - successCount,
                items);
    }
}
