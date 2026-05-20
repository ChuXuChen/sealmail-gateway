package com.sealmail.infra.mail.relay;

final class SmtpEnvelopeAddress {

    private SmtpEnvelopeAddress() {
    }

    static String requireValid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String trimmed = value.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch == '<' || ch == '>' || Character.isISOControl(ch)) {
                throw new IllegalArgumentException(field + " contains illegal SMTP envelope characters");
            }
        }
        return trimmed;
    }
}
