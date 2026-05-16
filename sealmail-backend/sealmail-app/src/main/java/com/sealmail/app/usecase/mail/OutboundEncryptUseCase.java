package com.sealmail.app.usecase.mail;

import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.certificate.CertificateChainService;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.CryptoProfileSelector;
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
    private final CryptoProfileSelector cryptoProfileSelector;

    @Transactional
    public byte[] execute(
            byte[] message,
            List<EmailAddress> recipients,
            UserContext user) {

        permissionChecker.checkCanViewQuarantine(user);

        log.debug("Attempting to encrypt message for {} recipients", recipients.size());

        Map<EmailAddress, List<Certificate>> certificatesByRecipient = new LinkedHashMap<>();
        for (EmailAddress recipient : recipients) {
            List<Certificate> certs = certificateRepository.findTrustedForEncryption(recipient);
            certs = certs.stream()
                    .filter(certificateChainService::isChainTrustedAndUsable)
                    .toList();
            if (certs.isEmpty()) {
                throw new RuntimeException("Encryption failed: no trusted certificate for recipient: " + recipient);
            }
            certificatesByRecipient.put(recipient, certs);
        }

        CryptoProfileSelector.EncryptionProfilePlan plan =
                cryptoProfileSelector.encryptionPlan(certificatesByRecipient, CryptoProfile.AUTO);
        if (!plan.success()) {
            throw new RuntimeException("Encryption failed: " + plan.failureDetail());
        }
        List<String> certPems = plan.certificates().values().stream()
                .map(Certificate::getPemContent)
                .toList();
        return smimeOperations.encryptMultiple(message, certPems, plan.profile());
    }
}
