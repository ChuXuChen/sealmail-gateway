package com.sealmail.domain.key;

public record KeyOperationResult(
        byte[] payload,
        KeyRecord keyRecord
) {
    public KeyOperationResult {
        if (payload == null) {
            throw new IllegalArgumentException("payload cannot be null");
        }
        if (keyRecord == null) {
            throw new IllegalArgumentException("keyRecord cannot be null");
        }
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
