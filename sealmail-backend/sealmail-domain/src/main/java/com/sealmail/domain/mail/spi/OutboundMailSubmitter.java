package com.sealmail.domain.mail.spi;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.List;
import java.util.Map;

public interface OutboundMailSubmitter {

    void submitPlain(PlainOutboundMailSubmission submission);

    void submitProtected(ProtectedOutboundMailSubmission submission);

    record PlainOutboundMailSubmission(
            byte[] mailContent,
            EmailAddress sender,
            List<EmailAddress> recipients
    ) {
        public PlainOutboundMailSubmission {
            if (mailContent == null) {
                throw new IllegalArgumentException("mailContent must not be null");
            }
            if (sender == null) {
                throw new IllegalArgumentException("sender must not be null");
            }
            recipients = recipients == null ? List.of() : List.copyOf(recipients);
        }
    }

    record ProtectedOutboundMailSubmission(
            byte[] mailContent,
            MailEnvelope envelope,
            boolean signingEnabled,
            boolean encryptionEnabled,
            CryptoProfile cryptoProfile,
            String senderCertificatePem,
            String senderCertificateThumbprint,
            Map<EmailAddress, String> recipientCertificates
    ) {
        public ProtectedOutboundMailSubmission {
            if (mailContent == null) {
                throw new IllegalArgumentException("mailContent must not be null");
            }
            if (envelope == null) {
                throw new IllegalArgumentException("envelope must not be null");
            }
            cryptoProfile = cryptoProfile == null ? CryptoProfile.AUTO : cryptoProfile;
            recipientCertificates = recipientCertificates == null
                    ? Map.of()
                    : Map.copyOf(recipientCertificates);
        }
    }
}
