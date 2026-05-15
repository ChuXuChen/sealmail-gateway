package com.sealmail.infra.mail.relay;

/**
 * Raised when a downstream SMTP relay interaction fails.
 */
public class SmtpRelayException extends Exception {

    public SmtpRelayException(String message) {
        super(message);
    }

    public SmtpRelayException(String message, Throwable cause) {
        super(message, cause);
    }
}
