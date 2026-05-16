package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;

public class CryptoProfileSelector {

    public Optional<CryptoProfile> profileOf(Certificate certificate) {
        if (certificate == null) {
            return Optional.empty();
        }
        return CryptoProfile.fromCertificateAlgorithm(certificate.getAlgorithm());
    }

    public Optional<Certificate> select(List<Certificate> certificates, CryptoProfile profile) {
        if (certificates == null || certificates.isEmpty() || profile == null) {
            return Optional.empty();
        }
        if (profile == CryptoProfile.AUTO) {
            return selectPreferred(certificates);
        }
        return certificates.stream()
                .filter(certificate -> profileOf(certificate).filter(profile::equals).isPresent())
                .findFirst();
    }

    public Optional<Certificate> selectPreferred(List<Certificate> certificates) {
        if (certificates == null || certificates.isEmpty()) {
            return Optional.empty();
        }
        Optional<Certificate> gm = select(certificates, CryptoProfile.GM);
        return gm.isPresent() ? gm : select(certificates, CryptoProfile.STANDARD);
    }

    public EncryptionProfilePlan encryptionPlan(Map<EmailAddress, List<Certificate>> certificatesByRecipient,
                                                CryptoProfile requestedProfile) {
        Map<EmailAddress, RecipientProfileOptions> recipientOptions = recipientOptions(certificatesByRecipient);
        if (recipientOptions.isEmpty()) {
            return EncryptionProfilePlan.failure("No recipients available for encryption");
        }

        CryptoProfile profile = requestedProfile != null ? requestedProfile : CryptoProfile.AUTO;
        if (profile == CryptoProfile.GM) {
            return planForProfile(recipientOptions, CryptoProfile.GM,
                    "以下收件人没有GM加密证书: ");
        }
        if (profile == CryptoProfile.STANDARD) {
            return planForProfile(recipientOptions, CryptoProfile.STANDARD,
                    "以下收件人没有STANDARD加密证书: ");
        }

        List<String> recipientsWithoutCertificates = recipientOptions.values().stream()
                .filter(option -> !option.supportsAny())
                .map(option -> option.recipient().getValue())
                .toList();
        if (!recipientsWithoutCertificates.isEmpty()) {
            if (recipientsWithoutCertificates.size() == recipientOptions.size()) {
                return EncryptionProfilePlan.failure("未找到收件人加密证书");
            }
            return EncryptionProfilePlan.failure("以下收件人没有加密证书: "
                    + String.join(", ", recipientsWithoutCertificates));
        }

        if (recipientOptions.values().stream().allMatch(option -> option.supports(CryptoProfile.GM))) {
            return planForProfile(recipientOptions, CryptoProfile.GM,
                    "以下收件人没有GM加密证书: ");
        }
        if (recipientOptions.values().stream().allMatch(option -> option.supports(CryptoProfile.STANDARD))) {
            return planForProfile(recipientOptions, CryptoProfile.STANDARD,
                    "以下收件人没有STANDARD加密证书: ");
        }

        return EncryptionProfilePlan.failure("多收件人无法共享同一加密Profile: "
                + capabilitySummary(recipientOptions));
    }

    public void assertCompatible(CryptoProfile profile, Certificate certificate) {
        if (profile == null || profile == CryptoProfile.AUTO || certificate == null) {
            return;
        }
        if (profileOf(certificate).filter(profile::equals).isEmpty()) {
            throw new CryptoProfileMismatchException(
                    "Certificate does not match crypto profile " + profile + ": "
                            + certificate.getId().getThumbprint());
        }
    }

    private EncryptionProfilePlan planForProfile(Map<EmailAddress, RecipientProfileOptions> recipientOptions,
                                                 CryptoProfile profile,
                                                 String missingPrefix) {
        List<String> missingRecipients = recipientOptions.values().stream()
                .filter(option -> !option.supports(profile))
                .map(option -> option.recipient().getValue())
                .toList();
        if (!missingRecipients.isEmpty()) {
            return EncryptionProfilePlan.failure(missingPrefix + String.join(", ", missingRecipients));
        }

        Map<EmailAddress, Certificate> certificates = new LinkedHashMap<>();
        for (RecipientProfileOptions option : recipientOptions.values()) {
            certificates.put(option.recipient(), option.certificateFor(profile));
        }
        return EncryptionProfilePlan.success(profile, certificates);
    }

    private Map<EmailAddress, RecipientProfileOptions> recipientOptions(
            Map<EmailAddress, List<Certificate>> certificatesByRecipient) {
        if (certificatesByRecipient == null || certificatesByRecipient.isEmpty()) {
            return Map.of();
        }
        Map<EmailAddress, RecipientProfileOptions> result = new LinkedHashMap<>();
        certificatesByRecipient.forEach((recipient, certificates) -> result.put(
                recipient,
                new RecipientProfileOptions(
                        recipient,
                        select(certificates, CryptoProfile.GM).orElse(null),
                        select(certificates, CryptoProfile.STANDARD).orElse(null))));
        return result;
    }

    private String capabilitySummary(Map<EmailAddress, RecipientProfileOptions> recipientOptions) {
        return recipientOptions.values().stream()
                .map(option -> option.recipient().getValue() + "=" + option.capabilityLabel())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    public record EncryptionProfilePlan(
            boolean success,
            CryptoProfile profile,
            Map<EmailAddress, Certificate> certificates,
            String failureDetail
    ) {

        public EncryptionProfilePlan {
            certificates = certificates == null || certificates.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(certificates));
        }

        public static EncryptionProfilePlan success(CryptoProfile profile,
                                                    Map<EmailAddress, Certificate> certificates) {
            if (profile == null || profile == CryptoProfile.AUTO) {
                throw new IllegalArgumentException("Concrete crypto profile is required");
            }
            return new EncryptionProfilePlan(true, profile, certificates, null);
        }

        public static EncryptionProfilePlan failure(String detail) {
            return new EncryptionProfilePlan(false, null, Map.of(), detail);
        }

        public Map<EmailAddress, String> certificatePems() {
            Map<EmailAddress, String> result = new LinkedHashMap<>();
            certificates.forEach((recipient, certificate) -> result.put(recipient, certificate.getPemContent()));
            return result;
        }

        public Map<EmailAddress, String> certificateThumbprints() {
            Map<EmailAddress, String> result = new LinkedHashMap<>();
            certificates.forEach((recipient, certificate) ->
                    result.put(recipient, certificate.getId().getThumbprint()));
            return result;
        }
    }

    private record RecipientProfileOptions(EmailAddress recipient,
                                           Certificate gmCertificate,
                                           Certificate standardCertificate) {

        boolean supports(CryptoProfile profile) {
            return certificateFor(profile) != null;
        }

        boolean supportsAny() {
            return gmCertificate != null || standardCertificate != null;
        }

        Certificate certificateFor(CryptoProfile profile) {
            return profile == CryptoProfile.GM ? gmCertificate : standardCertificate;
        }

        String capabilityLabel() {
            if (gmCertificate != null && standardCertificate != null) {
                return "GM,STANDARD";
            }
            if (gmCertificate != null) {
                return "GM";
            }
            if (standardCertificate != null) {
                return "STANDARD";
            }
            return "NONE";
        }
    }
}
