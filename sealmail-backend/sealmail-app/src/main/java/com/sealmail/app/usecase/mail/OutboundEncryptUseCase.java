package com.sealmail.app.usecase.mail;

import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.certificate.CertificateChainService;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEEncryptionSuite;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboundEncryptUseCase {

    private static final Logger log = LoggerFactory.getLogger(OutboundEncryptUseCase.class);

    private final SMIMEOperations smimeOperations;
    private final CertificateRepository certificateRepository;
    private final PermissionChecker permissionChecker;
    private final CertificateChainService certificateChainService;

    @Transactional
    public byte[] execute(
            byte[] message,
            List<EmailAddress> recipients,
            UserContext user) {

        permissionChecker.checkCanViewQuarantine(user);

        log.debug("Attempting to encrypt message for {} recipients", recipients.size());

        Map<EmailAddress, RecipientCertificateOptions> recipientOptions = new LinkedHashMap<>();
        for (EmailAddress recipient : recipients) {
            List<Certificate> certs = certificateRepository.findTrustedForEncryption(recipient);
            certs = certs.stream()
                    .filter(certificateChainService::isChainTrustedAndUsable)
                    .toList();
            if (certs.isEmpty()) {
                throw new RuntimeException("Encryption failed: no trusted certificate for recipient: " + recipient);
            }
            RecipientCertificateOptions options = new RecipientCertificateOptions(
                    selectCertificate(certs, SMIMEEncryptionSuite.GM),
                    selectCertificate(certs, SMIMEEncryptionSuite.STANDARD));
            if (!options.supportsAny()) {
                throw new RuntimeException("Encryption failed: no supported certificate for recipient: " + recipient);
            }
            recipientOptions.put(recipient, options);
        }

        SMIMEEncryptionSuite suite = detectSuite(recipientOptions);
        List<String> certPems = recipientOptions.values().stream()
                .map(options -> options.certificateFor(suite).getPemContent())
                .toList();
        return smimeOperations.encryptMultiple(message, certPems, suite);
    }

    private Certificate selectCertificate(List<Certificate> certs, SMIMEEncryptionSuite suite) {
        for (Certificate cert : certs) {
            if (matchesSuite(cert, suite)) {
                return cert;
            }
        }
        return null;
    }

    private SMIMEEncryptionSuite detectSuite(Map<EmailAddress, RecipientCertificateOptions> recipientOptions) {
        if (recipientOptions.values().stream().allMatch(RecipientCertificateOptions::supportsGm)) {
            return SMIMEEncryptionSuite.GM;
        }
        if (recipientOptions.values().stream().allMatch(RecipientCertificateOptions::supportsStandard)) {
            return SMIMEEncryptionSuite.STANDARD;
        }
        throw new RuntimeException("Recipients do not share one encryption suite: "
                + capabilitySummary(recipientOptions));
    }

    private boolean matchesSuite(Certificate cert, SMIMEEncryptionSuite suite) {
        return cert.supportsEncryptionSuite(suite);
    }

    private String capabilitySummary(Map<EmailAddress, RecipientCertificateOptions> recipientOptions) {
        return recipientOptions.entrySet().stream()
                .map(entry -> entry.getKey().getValue() + "=" + entry.getValue().capabilityLabel())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private record RecipientCertificateOptions(Certificate gmCertificate, Certificate standardCertificate) {
        boolean supportsAny() {
            return supportsGm() || supportsStandard();
        }

        boolean supportsGm() {
            return gmCertificate != null;
        }

        boolean supportsStandard() {
            return standardCertificate != null;
        }

        Certificate certificateFor(SMIMEEncryptionSuite suite) {
            return suite == SMIMEEncryptionSuite.GM ? gmCertificate : standardCertificate;
        }

        String capabilityLabel() {
            if (supportsGm() && supportsStandard()) {
                return "GM,STANDARD";
            }
            if (supportsGm()) {
                return "GM";
            }
            if (supportsStandard()) {
                return "STANDARD";
            }
            return "NONE";
        }
    }
}
