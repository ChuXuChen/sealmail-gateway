package com.sealmail.infra.mail;

import com.sealmail.app.usecase.quarantine.QuarantineMailReleaseRelay;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.PostfixProperties;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.infra.mail.relay.SmtpRelayClient;
import com.sealmail.infra.mail.relay.SmtpRelayConnectionSettings;
import com.sealmail.infra.mail.relay.SmtpRelayException;
import com.sealmail.infra.mail.relay.SmtpRelayRequest;
import org.springframework.stereotype.Component;

@Component
public class QuarantineMailReleaseRelayImpl implements QuarantineMailReleaseRelay {

    private final RelayProperties relayProperties;
    private final PostfixProperties postfixProperties;
    private final SmtpRelayClient smtpRelayClient;
    private final SMIMEOperations smimeOperations;
    private final CertificateRepository certificateRepository;

    public QuarantineMailReleaseRelayImpl(RelayProperties relayProperties,
                                          PostfixProperties postfixProperties,
                                          SmtpRelayClient smtpRelayClient,
                                          SMIMEOperations smimeOperations,
                                          CertificateRepository certificateRepository) {
        this.relayProperties = relayProperties;
        this.postfixProperties = postfixProperties;
        this.smtpRelayClient = smtpRelayClient;
        this.smimeOperations = smimeOperations;
        this.certificateRepository = certificateRepository;
    }

    @Override
    public void relay(QuarantinedMail mail, boolean encryptBeforeRelay) {
        SmtpRelayConnectionSettings connection = connectionSettings(mail);
        SmtpRelayRequest request = new SmtpRelayRequest(
                connection,
                envelopeFrom(mail),
                mail.getRecipients().stream()
                        .map(address -> address.getValue())
                        .toList(),
                releasePayload(mail, encryptBeforeRelay)
        );
        try {
            smtpRelayClient.send(request);
        } catch (SmtpRelayException e) {
            throw new QuarantineReleaseRelayException(mail.getId(), e);
        }
    }

    private byte[] releasePayload(QuarantinedMail mail, boolean encryptBeforeRelay) {
        if (!encryptBeforeRelay) {
            return mail.getRawContent();
        }

        try {
            return smimeOperations.encryptMultiple(mail.getRawContent(), encryptionCertificates(mail));
        } catch (QuarantineReleaseEncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new QuarantineReleaseEncryptionException(mail.getId(), e);
        }
    }

    private java.util.List<String> encryptionCertificates(QuarantinedMail mail) {
        java.util.List<String> certificates = new java.util.ArrayList<>();
        java.util.List<String> missingRecipients = new java.util.ArrayList<>();
        for (EmailAddress recipient : mail.getRecipients()) {
            certificateRepository.findTrustedForEncryption(recipient).stream()
                    .findFirst()
                    .map(Certificate::getPemContent)
                    .ifPresentOrElse(certificates::add,
                            () -> missingRecipients.add(recipient.getValue()));
        }
        if (!missingRecipients.isEmpty()) {
            throw new QuarantineReleaseEncryptionException(
                    mail.getId(),
                    "Missing trusted encryption certificate for: " + String.join(", ", missingRecipients));
        }
        return certificates;
    }

    private SmtpRelayConnectionSettings connectionSettings(QuarantinedMail mail) {
        if (postfixProperties.isEnabled()) {
            return new SmtpRelayConnectionSettings(
                    postfixProperties.getHost(),
                    postfixPort(mail),
                    postfixProperties.isUseTls(),
                    "",
                    "",
                    postfixProperties.getTimeout()
            );
        }
        return new SmtpRelayConnectionSettings(
                relayProperties.getHost(),
                relayProperties.getPort(),
                relayProperties.isUseTls(),
                relayProperties.getUsername(),
                relayProperties.getPassword(),
                relayProperties.getTimeout()
        );
    }

    private int postfixPort(QuarantinedMail mail) {
        return mail.getDirection() == MailDirection.OUTBOUND
                ? postfixProperties.getOutboundPort()
                : postfixProperties.getAfterFilterPort();
    }

    private String envelopeFrom(QuarantinedMail mail) {
        if (postfixProperties.isEnabled()
                && postfixProperties.getEnvelopeFrom() != null
                && !postfixProperties.getEnvelopeFrom().isBlank()) {
            return postfixProperties.getEnvelopeFrom();
        }
        if (!postfixProperties.isEnabled()
                && relayProperties.getUsername() != null
                && !relayProperties.getUsername().isBlank()) {
            return relayProperties.getUsername();
        }
        return mail.getSender().getValue();
    }
}
