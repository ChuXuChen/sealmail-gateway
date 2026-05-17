package com.sealmail.domain.mailauth;

public record SigningResult(
        byte[] content,
        boolean signed,
        String domainName,
        String selector,
        String detail
) {

    public SigningResult {
        content = content != null ? content.clone() : new byte[0];
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
