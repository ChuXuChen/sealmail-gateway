package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.shared.event.DomainEvent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a pipeline step execution.
 */
public record PipelineResult(
        boolean success,
        byte[] payload,
        String errorMessage,
        boolean requiresQuarantine,
        String quarantineReason,
        String quarantineDetail,
        MailRecordDisposition recordDisposition,
        String headerName,
        String headerValue,
        Map<String, Object> headers,
        List<DomainEvent> events
) {

    public static PipelineResult success(byte[] payload) {
        return success(payload, List.of());
    }

    public static PipelineResult success(byte[] payload, DomainEvent... events) {
        return success(payload, Arrays.asList(events));
    }

    public static PipelineResult success(byte[] payload, List<? extends DomainEvent> events) {
        return new PipelineResult(true, payload, null, false, null, null, null, null, null, Map.of(),
                events != null ? List.copyOf(events) : List.of());
    }

    public static PipelineResult successWithHeader(byte[] payload, String headerName, String headerValue) {
        return successWithHeaders(payload, Map.of(headerName, headerValue));
    }

    public static PipelineResult successWithHeaders(byte[] payload, Map<String, ?> headers) {
        Map<String, Object> copiedHeaders = copyHeaders(headers);
        String firstHeaderName = copiedHeaders.keySet().stream().findFirst().orElse(null);
        Object firstHeaderValue = firstHeaderName != null ? copiedHeaders.get(firstHeaderName) : null;
        return new PipelineResult(
                true,
                payload,
                null,
                false,
                null,
                null,
                null,
                firstHeaderName,
                firstHeaderValue != null ? firstHeaderValue.toString() : null,
                copiedHeaders,
                List.of());
    }

    private static Map<String, Object> copyHeaders(Map<String, ?> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copiedHeaders = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            if (name != null && value != null) {
                copiedHeaders.put(name, value);
            }
        });
        return Map.copyOf(copiedHeaders);
    }

    public static PipelineResult failure(String errorMessage) {
        return new PipelineResult(false, null, errorMessage, false, null, null, null, null, null, Map.of(), List.of());
    }

    public static PipelineResult quarantine(String reason) {
        return quarantine(null, reason, reason);
    }

    public static PipelineResult quarantine(String reason, String detail) {
        return quarantine(null, reason, detail);
    }

    public static PipelineResult quarantine(byte[] payload, String reason) {
        return quarantine(payload, reason, reason);
    }

    public static PipelineResult quarantine(byte[] payload, String reason, String detail) {
        return quarantine(payload, reason, detail, MailRecordDisposition.EXCEPTION);
    }

    public static PipelineResult quarantine(byte[] payload,
                                            String reason,
                                            String detail,
                                            MailRecordDisposition recordDisposition) {
        return new PipelineResult(false, payload, detail, true, reason, detail, recordDisposition, null, null, Map.of(), List.of());
    }

    public static final String SUCCESS = "SUCCESS";
}
