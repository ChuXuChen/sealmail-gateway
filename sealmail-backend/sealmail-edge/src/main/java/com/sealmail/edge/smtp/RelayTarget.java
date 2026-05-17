package com.sealmail.edge.smtp;

public record RelayTarget(String host, int port, Security security) {
    public RelayTarget {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    public boolean startTlsRequired() {
        return security == Security.STARTTLS;
    }

    public boolean implicitTls() {
        return security == Security.IMPLICIT_TLS;
    }

    public enum Security {
        CLEAR,
        STARTTLS,
        IMPLICIT_TLS
    }
}
