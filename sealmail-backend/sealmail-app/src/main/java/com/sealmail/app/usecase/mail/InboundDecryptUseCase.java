package com.sealmail.app.usecase.mail;

import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.certificate.CertificateChainService;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InboundDecryptUseCase {

    private static final Logger log = LoggerFactory.getLogger(InboundDecryptUseCase.class);

    private final SMIMEOperations smimeOperations;
    private final CertificateRepository certificateRepository;
    private final PermissionChecker permissionChecker;
    private final CertificateChainService certificateChainService;

    @Transactional
    public byte[] execute(
            byte[] encryptedMessage,
            EmailAddress recipient,
            String privateKeyPem,
            UserContext user) {

        permissionChecker.checkCanViewQuarantine(user);

        log.debug("Attempting to decrypt message for recipient: {}", recipient);

        try {
            // Find recipient certificate
            var certs = certificateRepository.findTrustedForEncryption(recipient);
            certs = certs.stream()
                    .filter(certificateChainService::isChainTrustedAndUsable)
                    .toList();
            if (certs.isEmpty()) {
                log.warn("No trusted certificate found for recipient: {}", recipient);
                return encryptedMessage; // Return as-is if no cert
            }

            // Get certificate PEM data
            String certPem = certs.get(0).getPemContent();

            // Decrypt
            byte[] decrypted = smimeOperations.decrypt(encryptedMessage, privateKeyPem, certPem);

            log.info("Successfully decrypted message for recipient: {}", recipient);

            return decrypted;

        } catch (Exception e) {
            log.error("Failed to decrypt message for recipient: {}: {}",
                    recipient, e.getMessage(), e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
