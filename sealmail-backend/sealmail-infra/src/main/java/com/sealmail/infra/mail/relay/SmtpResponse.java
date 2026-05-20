package com.sealmail.infra.mail.relay;

import java.util.List;

record SmtpResponse(int code, List<String> lines, boolean ehloSucceeded) {

    SmtpResponse(int code, List<String> lines) {
        this(code, lines, false);
    }

    SmtpResponse withEhloSucceeded(boolean value) {
        return new SmtpResponse(code, lines, value);
    }

    String singleLine() {
        return code + " " + String.join(" | ", lines);
    }
}
