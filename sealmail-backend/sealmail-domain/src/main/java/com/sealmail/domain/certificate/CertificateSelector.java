package com.sealmail.domain.certificate;

import com.sealmail.domain.shared.model.EmailAddress;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CertificateSelector {

    public Optional<Certificate> selectForEncryption(EmailAddress recipient, List<Certificate> candidates) {
        return candidates.stream()
                .filter(Certificate::isSuitableForEncryption)
                .filter(cert -> cert.getOwner().equals(recipient))
                .max(Comparator.comparing(cert -> cert.getValidity().getNotAfter()));
    }

    public Optional<Certificate> selectForSigning(EmailAddress sender, List<Certificate> candidates) {
        return candidates.stream()
                .filter(Certificate::isSuitableForSigning)
                .filter(cert -> cert.getOwner().equals(sender))
                .max(Comparator.comparing(cert -> cert.getValidity().getNotAfter()));
    }
}
