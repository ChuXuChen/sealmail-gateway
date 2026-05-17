package com.sealmail.edge.smtp;

import java.util.List;

public record SmtpReply(int code, List<String> lines) {
    public SmtpReply {
        lines = List.copyOf(lines);
    }

    public boolean positiveCompletion() {
        return code >= 200 && code < 300;
    }

    public boolean temporaryFailure() {
        return code >= 400 && code < 500;
    }

    public boolean permanentFailure() {
        return code >= 500 && code < 600;
    }

    public String singleLine() {
        return lines.isEmpty() ? Integer.toString(code) : lines.get(lines.size() - 1);
    }
}
