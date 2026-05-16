package com.sealmail.app.dto.response;

public record CrlContentResponse(
        byte[] der,
        String pem
) {
}
