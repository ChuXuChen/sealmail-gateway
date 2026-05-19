package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.mailauth.AuthenticationResultSet;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.List;

public record MailSecurityContext(
        CryptoProfile cryptoProfile,
        CertificateSelection certificateSelection,
        MailProcessingDecision decision,
        AuthenticationResultSet mailAuthResults,
        List<EmailAddress> smimeEncryptedRecipients
) {

    public MailSecurityContext {
        cryptoProfile = cryptoProfile != null ? cryptoProfile : CryptoProfile.AUTO;
        certificateSelection = certificateSelection != null ? certificateSelection : CertificateSelection.empty();
        decision = decision != null ? decision : MailProcessingDecision.none();
        mailAuthResults = mailAuthResults != null ? mailAuthResults : AuthenticationResultSet.empty();
        smimeEncryptedRecipients = smimeEncryptedRecipients != null ? List.copyOf(smimeEncryptedRecipients) : List.of();
    }
}
