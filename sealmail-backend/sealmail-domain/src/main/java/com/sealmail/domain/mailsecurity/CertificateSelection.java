package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.shared.model.EmailAddress;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CertificateSelection(
        String senderCertificatePem,
        String senderCertificateThumbprint,
        String recipientCertificatePem,
        String recipientCertificateThumbprint,
        Map<EmailAddress, String> recipientCertificates,
        Map<EmailAddress, String> recipientCertificateThumbprints
) {

    public CertificateSelection {
        recipientCertificates = copy(recipientCertificates);
        recipientCertificateThumbprints = copy(recipientCertificateThumbprints);
    }

    public static CertificateSelection empty() {
        return new CertificateSelection(null, null, null, null, Map.of(), Map.of());
    }

    public CertificateSelection withSenderCertificate(String pem, String thumbprint) {
        return new CertificateSelection(pem, thumbprint, recipientCertificatePem, recipientCertificateThumbprint,
                recipientCertificates, recipientCertificateThumbprints);
    }

    public CertificateSelection withRecipientCertificate(String pem, String thumbprint) {
        return new CertificateSelection(senderCertificatePem, senderCertificateThumbprint, pem, thumbprint,
                recipientCertificates, recipientCertificateThumbprints);
    }

    public CertificateSelection withRecipientCertificates(Map<EmailAddress, String> certificates,
                                                          Map<EmailAddress, String> thumbprints) {
        return new CertificateSelection(senderCertificatePem, senderCertificateThumbprint,
                recipientCertificatePem, recipientCertificateThumbprint, certificates, thumbprints);
    }

    private static Map<EmailAddress, String> copy(Map<EmailAddress, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
