package com.sealmail.infra.crypto;

import com.sealmail.domain.mailsecurity.CryptoProfile;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.gm.GMObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class X509CryptoProfileResolver {

    private final List<ProfileRule> rules;

    X509CryptoProfileResolver() {
        this(List.of(
                new ProfileRule(CryptoProfile.GM, X509CryptoProfileResolver::hasSm2SubjectPublicKey),
                new ProfileRule(CryptoProfile.STANDARD, X509CryptoProfileResolver::hasRsaSubjectPublicKey)
        ));
    }

    X509CryptoProfileResolver(List<ProfileRule> rules) {
        this.rules = List.copyOf(rules);
    }

    Optional<CryptoProfile> resolve(X509Certificate certificate) {
        if (certificate == null) {
            return Optional.empty();
        }
        return rules.stream()
                .filter(rule -> rule.matches(certificate))
                .map(ProfileRule::profile)
                .findFirst();
    }

    CryptoProfile requireProfile(X509Certificate certificate) {
        return resolve(certificate)
                .orElseThrow(() -> new BcSMIMEOperations.CryptoException(
                        "Unsupported certificate crypto profile: "
                                + (certificate == null ? "null" : certificate.getPublicKey().getAlgorithm())));
    }

    boolean matches(X509Certificate certificate, CryptoProfile profile) {
        return resolve(certificate).filter(profile::equals).isPresent();
    }

    private static boolean hasRsaSubjectPublicKey(X509Certificate certificate) {
        return "RSA".equals(normalizedPublicKeyAlgorithm(certificate));
    }

    private static boolean hasSm2SubjectPublicKey(X509Certificate certificate) {
        if ("SM2".equals(normalizedPublicKeyAlgorithm(certificate))) {
            return true;
        }
        try {
            X509CertificateHolder holder = new X509CertificateHolder(certificate.getEncoded());
            ASN1ObjectIdentifier algorithm = holder.getSubjectPublicKeyInfo()
                    .getAlgorithm()
                    .getAlgorithm();
            Object parameters = holder.getSubjectPublicKeyInfo()
                    .getAlgorithm()
                    .getParameters();
            String parameterText = parameters == null ? "" : parameters.toString();
            return GMObjectIdentifiers.sm2p256v1.equals(algorithm)
                    || GMObjectIdentifiers.sm2p256v1.getId().equals(parameterText)
                    || parameterText.toLowerCase(Locale.ROOT).contains("sm2p256v1");
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizedPublicKeyAlgorithm(X509Certificate certificate) {
        String algorithm = certificate.getPublicKey().getAlgorithm();
        return algorithm == null ? "UNKNOWN" : algorithm.trim().toUpperCase(Locale.ROOT);
    }

    record ProfileRule(CryptoProfile profile, ProfileMatcher matcher) {
        boolean matches(X509Certificate certificate) {
            return matcher.matches(certificate);
        }
    }

    @FunctionalInterface
    interface ProfileMatcher {
        boolean matches(X509Certificate certificate);
    }
}
