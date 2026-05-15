package com.sealmail.app.usecase.mail;

import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.certificate.CertificateChainService;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        byte[] result = message;

        for (EmailAddress recipient : recipients) {
            List<Certificate> certs = certificateRepository.findTrustedForEncryption(recipient);
            certs = certs.stream()
                    .filter(certificateChainService::isChainTrustedAndUsable)
                    .toList();
            if (certs.isEmpty()) {
                log.warn("No trusted certificate found for recipient: {}, skipping encryption",
                        recipient);
                continue;
            }

            try {
                String certPem = certs.get(0).getPemContent();
                result = smimeOperations.encrypt(result, certPem);
                log.debug("Successfully encrypted message for recipient: {}", recipient);
            } catch (Exception e) {
                log.error("Failed to encrypt message for recipient: {}: {}",
                        recipient, e.getMessage(), e);
                throw new RuntimeException("Encryption failed for recipient: " + recipient, e);
            }
        }

        return result;
    }
}
