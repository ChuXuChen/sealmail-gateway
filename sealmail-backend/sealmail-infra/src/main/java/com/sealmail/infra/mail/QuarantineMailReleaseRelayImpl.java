package com.sealmail.infra.mail;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.quarantine.spi.QuarantineMailReleaseRelay;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases DLP-quarantined mail by resuming the normal delivery path after DLP.
 */
@Component
public class QuarantineMailReleaseRelayImpl implements QuarantineMailReleaseRelay {

    private final MessageChannel quarantineReleaseChannel;

    public QuarantineMailReleaseRelayImpl(
            @Qualifier("quarantineReleaseChannel") MessageChannel quarantineReleaseChannel) {
        this.quarantineReleaseChannel = quarantineReleaseChannel;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void relay(QuarantinedMail mail, boolean encryptBeforeRelay) {
        Message<byte[]> message = releaseMessage(mail, encryptBeforeRelay);
        try {
            boolean sent = quarantineReleaseChannel.send(message);
            if (!sent) {
                throw new QuarantineReleaseRelayException(mail.getId(), "Failed to enqueue quarantined mail release");
            }
        } catch (QuarantineReleaseRelayException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QuarantineReleaseRelayException(mail.getId(), e);
        }
    }

    private Message<byte[]> releaseMessage(QuarantinedMail mail, boolean encryptBeforeRelay) {
        MailEnvelope envelope = envelope(mail);
        MailProcessingContext context = MailProcessingContext.initial(
                envelope,
                direction(mail),
                "dlp_quarantine_release",
                mail.getRawContent(),
                mail.getSubject(),
                mail.getRemoteAddress());
        context = context.withQuarantineReleaseId(mail.getId());
        if (encryptBeforeRelay) {
            context = context.withDecision(context.decision()
                    .withEncryptionRequired(true)
                    .withMustEncrypt(true));
        }
        return MessageBuilder.withPayload(mail.getRawContent())
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private MailEnvelope envelope(QuarantinedMail mail) {
        return new MailEnvelope(
                messageId(mail),
                mail.getSender(),
                mail.getRecipients(),
                mail.getRemoteAddress(),
                "dlp-quarantine-release",
                mail.getCreatedAt(),
                mail.getRawContent()
        );
    }

    private String messageId(QuarantinedMail mail) {
        String value = mail.getMessageId();
        if (value != null && !value.isBlank()) {
            return value;
        }
        return mail.getId() + "@sealmail.local";
    }

    private MailDirection direction(QuarantinedMail mail) {
        return mail.getDirection() != null ? mail.getDirection() : MailDirection.OUTBOUND;
    }
}
