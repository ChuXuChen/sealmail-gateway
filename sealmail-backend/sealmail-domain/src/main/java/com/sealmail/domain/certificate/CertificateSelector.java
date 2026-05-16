package com.sealmail.domain.certificate;

import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.CryptoProfileSelector;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CertificateSelector {

    private final CryptoProfileSelector cryptoProfileSelector;

    public CertificateSelector() {
        this(new CryptoProfileSelector());
    }

    public CertificateSelector(CryptoProfileSelector cryptoProfileSelector) {
        this.cryptoProfileSelector = cryptoProfileSelector;
    }

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

    public Optional<Certificate> selectForEncryption(EmailAddress recipient,
                                                     List<Certificate> candidates,
                                                     CryptoProfile profile) {
        return cryptoProfileSelector.select(
                encryptionCandidates(recipient, candidates),
                profile);
    }

    public Optional<Certificate> selectForSigning(EmailAddress sender,
                                                  List<Certificate> candidates,
                                                  CryptoProfile profile) {
        return cryptoProfileSelector.select(
                signingCandidates(sender, candidates),
                profile);
    }

    private List<Certificate> encryptionCandidates(EmailAddress recipient, List<Certificate> candidates) {
        return candidates.stream()
                .filter(Certificate::isSuitableForEncryption)
                .filter(cert -> cert.getOwner().equals(recipient))
                .toList();
    }

    private List<Certificate> signingCandidates(EmailAddress sender, List<Certificate> candidates) {
        return candidates.stream()
                .filter(Certificate::isSuitableForSigning)
                .filter(cert -> cert.getOwner().equals(sender))
                .toList();
    }
}
