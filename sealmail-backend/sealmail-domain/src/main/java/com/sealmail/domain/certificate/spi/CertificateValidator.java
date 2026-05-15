package com.sealmail.domain.certificate.spi;

public interface CertificateValidator {

    ValidationResult validate(String pemCert);

    record ValidationResult(boolean isValid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failed(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
