package com.sealmail.domain.mailsecurity;

import java.util.List;

public record MailProcessingStatusSnapshot(
        MailAuthStatus mailAuth,
        CertificateStatus certificate,
        SmimeStatus smime,
        DlpStatus dlp,
        DeliveryStatus delivery,
        FinalDispositionStatus finalDisposition,
        FailureStatus failure
) {

    public static final String PENDING = "PENDING";
    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";
    public static final String SKIPPED = "SKIPPED";
    public static final String DELIVERED = "DELIVERED";
    public static final String QUARANTINED = "QUARANTINED";
    public static final String EXCEPTION = "EXCEPTION";

    public MailProcessingStatusSnapshot {
        mailAuth = mailAuth != null ? mailAuth : MailAuthStatus.pending();
        certificate = certificate != null ? certificate : CertificateStatus.pending();
        smime = smime != null ? smime : SmimeStatus.pending();
        dlp = dlp != null ? dlp : DlpStatus.pending();
        delivery = delivery != null ? delivery : DeliveryStatus.pending();
        finalDisposition = finalDisposition != null ? finalDisposition : FinalDispositionStatus.pending();
        failure = failure != null ? failure : FailureStatus.pending();
    }

    public static MailProcessingStatusSnapshot empty() {
        return new MailProcessingStatusSnapshot(
                MailAuthStatus.pending(),
                CertificateStatus.pending(),
                SmimeStatus.pending(),
                DlpStatus.pending(),
                DeliveryStatus.pending(),
                FinalDispositionStatus.pending(),
                FailureStatus.pending());
    }

    public MailProcessingStatusSnapshot withMailAuth(MailAuthStatus value) {
        return new MailProcessingStatusSnapshot(value, certificate, smime, dlp, delivery, finalDisposition, failure);
    }

    public MailProcessingStatusSnapshot withCertificate(CertificateStatus value) {
        return new MailProcessingStatusSnapshot(mailAuth, value, smime, dlp, delivery, finalDisposition, failure);
    }

    public MailProcessingStatusSnapshot withSmime(SmimeStatus value) {
        return new MailProcessingStatusSnapshot(mailAuth, certificate, value, dlp, delivery, finalDisposition, failure);
    }

    public MailProcessingStatusSnapshot withDlp(DlpStatus value) {
        return new MailProcessingStatusSnapshot(mailAuth, certificate, smime, value, delivery, finalDisposition, failure);
    }

    public MailProcessingStatusSnapshot withDelivery(DeliveryStatus value) {
        return new MailProcessingStatusSnapshot(mailAuth, certificate, smime, dlp, value, finalDisposition, failure);
    }

    public MailProcessingStatusSnapshot withFinalDisposition(FinalDispositionStatus value) {
        return new MailProcessingStatusSnapshot(mailAuth, certificate, smime, dlp, delivery, value, failure);
    }

    public MailProcessingStatusSnapshot withFailure(FailureStatus value) {
        return new MailProcessingStatusSnapshot(mailAuth, certificate, smime, dlp, delivery, finalDisposition, value);
    }

    public record MailAuthStatus(
            String status,
            AuthMechanismStatus spf,
            AuthMechanismStatus dkim,
            AuthMechanismStatus dmarc,
            String action,
            String reason,
            String detail
    ) {
        public MailAuthStatus {
            status = status != null ? status : PENDING;
        }

        public static MailAuthStatus pending() {
            return new MailAuthStatus(PENDING, null, null, null, null, null, null);
        }
    }

    public record AuthMechanismStatus(
            String result,
            String domain,
            String identity,
            String detail
    ) {
    }

    public record CertificateStatus(
            String status,
            String cryptoProfile,
            String senderThumbprint,
            List<RecipientCertificateStatus> recipients,
            List<String> missingRecipients,
            boolean senderMissing,
            boolean recipientMissing,
            String failureReason
    ) {
        public CertificateStatus {
            status = status != null ? status : PENDING;
            recipients = recipients != null ? List.copyOf(recipients) : List.of();
            missingRecipients = missingRecipients != null ? List.copyOf(missingRecipients) : List.of();
        }

        public static CertificateStatus pending() {
            return new CertificateStatus(PENDING, null, null, List.of(), List.of(), false, false, null);
        }
    }

    public record RecipientCertificateStatus(
            String email,
            String thumbprint,
            boolean selected
    ) {
    }

    public record SmimeStatus(
            OperationStatus sign,
            OperationStatus encrypt,
            OperationStatus verify,
            OperationStatus decrypt
    ) {
        public SmimeStatus {
            sign = sign != null ? sign : OperationStatus.pending();
            encrypt = encrypt != null ? encrypt : OperationStatus.pending();
            verify = verify != null ? verify : OperationStatus.pending();
            decrypt = decrypt != null ? decrypt : OperationStatus.pending();
        }

        public static SmimeStatus pending() {
            return new SmimeStatus(
                    OperationStatus.pending(),
                    OperationStatus.pending(),
                    OperationStatus.pending(),
                    OperationStatus.pending());
        }

        public SmimeStatus withSign(OperationStatus value) {
            return new SmimeStatus(value, encrypt, verify, decrypt);
        }

        public SmimeStatus withEncrypt(OperationStatus value) {
            return new SmimeStatus(sign, value, verify, decrypt);
        }

        public SmimeStatus withVerify(OperationStatus value) {
            return new SmimeStatus(sign, encrypt, value, decrypt);
        }

        public SmimeStatus withDecrypt(OperationStatus value) {
            return new SmimeStatus(sign, encrypt, verify, value);
        }
    }

    public record OperationStatus(
            String status,
            String algorithmSuite,
            String certificateThumbprint,
            List<String> recipientThumbprints,
            List<String> recipients,
            String failureReason
    ) {
        public OperationStatus {
            status = status != null ? status : PENDING;
            recipientThumbprints = recipientThumbprints != null ? List.copyOf(recipientThumbprints) : List.of();
            recipients = recipients != null ? List.copyOf(recipients) : List.of();
        }

        public static OperationStatus pending() {
            return new OperationStatus(PENDING, null, null, List.of(), List.of(), null);
        }

        public static OperationStatus skipped(String reason) {
            return new OperationStatus(SKIPPED, null, null, List.of(), List.of(), reason);
        }
    }

    public record DlpStatus(
            String status,
            String action,
            String recommendedAction,
            Integer maxSeverity,
            Integer matchCount,
            List<String> ruleNames,
            String eventId,
            Boolean monitorMode,
            String ubaRiskLevel,
            Boolean ubaActionUpgraded,
            String summary,
            String failureReason
    ) {
        public DlpStatus {
            status = status != null ? status : PENDING;
            ruleNames = ruleNames != null ? List.copyOf(ruleNames) : List.of();
        }

        public static DlpStatus pending() {
            return new DlpStatus(PENDING, null, null, null, null, List.of(), null, null, null, null, null, null);
        }
    }

    public record DeliveryStatus(
            String status,
            String route,
            String relayHost,
            Integer relayPort,
            String transportProfile,
            String targetId,
            String targetType,
            String reason,
            String detail
    ) {
        public DeliveryStatus {
            status = status != null ? status : PENDING;
        }

        public static DeliveryStatus pending() {
            return new DeliveryStatus(PENDING, null, null, null, null, null, null, null, null);
        }
    }

    public record FinalDispositionStatus(
            String status,
            String result,
            String action,
            String reason,
            String detail
    ) {
        public FinalDispositionStatus {
            status = status != null ? status : PENDING;
        }

        public static FinalDispositionStatus pending() {
            return new FinalDispositionStatus(PENDING, null, null, null, null);
        }
    }

    public record FailureStatus(
            String status,
            String step,
            String errorType,
            String reason,
            String detail,
            Boolean retryable
    ) {
        public FailureStatus {
            status = status != null ? status : PENDING;
        }

        public static FailureStatus pending() {
            return new FailureStatus(PENDING, null, null, null, null, null);
        }
    }
}
