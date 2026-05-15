package com.sealmail.domain.certificate.spi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 签名验证结果
 */
public class SignatureValidationResult {

    private boolean valid;
    private String signer;
    private String signerEmail;
    private Instant signingTime;
    private String signatureAlgorithm;
    private List<String> validationErrors = new ArrayList<>();
    private boolean certificateTrusted;
    private boolean certificateRevoked;
    private boolean certificateExpired;

    public boolean isTrusted() {
        return valid && certificateTrusted && !certificateRevoked && !certificateExpired;
    }

    // Getters
    public boolean isValid() { return valid; }
    public String getSigner() { return signer; }
    public String getSignerEmail() { return signerEmail; }
    public Instant getSigningTime() { return signingTime; }
    public String getSignatureAlgorithm() { return signatureAlgorithm; }
    public List<String> getValidationErrors() { return validationErrors; }
    public boolean isCertificateTrusted() { return certificateTrusted; }
    public boolean isCertificateRevoked() { return certificateRevoked; }
    public boolean isCertificateExpired() { return certificateExpired; }

    // Setters
    public void setValid(boolean valid) { this.valid = valid; }
    public void setSigner(String signer) { this.signer = signer; }
    public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
    public void setSigningTime(Instant signingTime) { this.signingTime = signingTime; }
    public void setSignatureAlgorithm(String signatureAlgorithm) { this.signatureAlgorithm = signatureAlgorithm; }
    public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; }
    public void setCertificateTrusted(boolean certificateTrusted) { this.certificateTrusted = certificateTrusted; }
    public void setCertificateRevoked(boolean certificateRevoked) { this.certificateRevoked = certificateRevoked; }
    public void setCertificateExpired(boolean certificateExpired) { this.certificateExpired = certificateExpired; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SignatureValidationResult result = new SignatureValidationResult();

        public Builder valid(boolean valid) { result.valid = valid; return this; }
        public Builder signer(String signer) { result.signer = signer; return this; }
        public Builder signerEmail(String signerEmail) { result.signerEmail = signerEmail; return this; }
        public Builder signingTime(Instant signingTime) { result.signingTime = signingTime; return this; }
        public Builder signatureAlgorithm(String signatureAlgorithm) { result.signatureAlgorithm = signatureAlgorithm; return this; }
        public Builder validationErrors(List<String> errors) { result.validationErrors = errors; return this; }
        public Builder certificateTrusted(boolean trusted) { result.certificateTrusted = trusted; return this; }
        public Builder certificateRevoked(boolean revoked) { result.certificateRevoked = revoked; return this; }
        public Builder certificateExpired(boolean expired) { result.certificateExpired = expired; return this; }

        public SignatureValidationResult build() {
            return result;
        }
    }
}
