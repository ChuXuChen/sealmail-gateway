package com.sealmail.infra.mail.auth;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.infra.config.properties.MailAuthProperties;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;

@Service
public class MailAuthenticationService {

    private final MailAuthProperties properties;
    private final SpfVerifier spfVerifier;
    private final DkimVerifier dkimVerifier;
    private final DmarcVerifier dmarcVerifier;

    public MailAuthenticationService(MailAuthProperties properties,
                                     SpfVerifier spfVerifier,
                                     DkimVerifier dkimVerifier,
                                     DmarcVerifier dmarcVerifier) {
        this.properties = properties;
        this.spfVerifier = spfVerifier;
        this.dkimVerifier = dkimVerifier;
        this.dmarcVerifier = dmarcVerifier;
    }

    public MailAuthenticationResult authenticate(byte[] content, MailEnvelope envelope) {
        if (!properties.isEnabled()) {
            return new MailAuthenticationResult(AuthResult.NONE, AuthResult.NONE, AuthResult.NONE,
                    DmarcPolicy.NONE, "", "mail authentication disabled", false);
        }
        try {
            MimeMessage message = MimeMessageSupport.parse(content);
            String fromDomain = MimeMessageSupport.fromDomain(message);
            String mailFromDomain = envelope != null ? envelope.getSender().getDomain() : null;
            String remoteHost = envelope != null ? envelope.getRemoteHost() : null;
            AuthResult spf = spfVerifier.verify(remoteHost, mailFromDomain);
            AuthResult dkim = dkimVerifier.verify(content);
            String dkimDomain = dkimVerifier.signingDomain(content);
            DmarcVerifier.DmarcDecision dmarc = dmarcVerifier.verify(fromDomain, mailFromDomain, dkimDomain, spf, dkim);
            String header = authenticationResultsHeader(spf, dkim, dmarc.result(), fromDomain, mailFromDomain);
            String detail = "spf=" + spf + ", dkim=" + dkim + ", dmarc=" + dmarc.result()
                    + ", policy=" + dmarc.policy();
            return new MailAuthenticationResult(
                    spf,
                    dkim,
                    dmarc.result(),
                    dmarc.policy(),
                    header,
                    detail,
                    properties.getDmarc().isQuarantineRejectPolicy()
            );
        } catch (Exception e) {
            String header = properties.getAuthservId() + "; spf=temperror dkim=temperror dmarc=temperror";
            return new MailAuthenticationResult(AuthResult.TEMPERROR, AuthResult.TEMPERROR, AuthResult.TEMPERROR,
                    DmarcPolicy.NONE, header, e.getMessage(), false);
        }
    }

    private String authenticationResultsHeader(AuthResult spf, AuthResult dkim, AuthResult dmarc,
                                               String fromDomain, String mailFromDomain) {
        return properties.getAuthservId()
                + "; spf=" + token(spf) + " smtp.mailfrom=" + value(mailFromDomain)
                + "; dkim=" + token(dkim)
                + "; dmarc=" + token(dmarc) + " header.from=" + value(fromDomain);
    }

    private String token(AuthResult result) {
        return result.name().toLowerCase();
    }

    private String value(String value) {
        return value != null ? value : "unknown";
    }
}
