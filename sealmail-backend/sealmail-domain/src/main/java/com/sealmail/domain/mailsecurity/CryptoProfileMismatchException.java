package com.sealmail.domain.mailsecurity;

public class CryptoProfileMismatchException extends RuntimeException {

    public CryptoProfileMismatchException(String message) {
        super(message);
    }
}
