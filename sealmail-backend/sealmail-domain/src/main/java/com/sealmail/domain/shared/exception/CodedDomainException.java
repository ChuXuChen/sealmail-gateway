package com.sealmail.domain.shared.exception;

public class CodedDomainException extends DomainException {

    private final String code;

    public CodedDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public CodedDomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
