package com.sealmail.app.usecase.mail;

import com.sealmail.app.dto.request.SendMailRequest;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.mail.spi.MailMessageComposer;
import com.sealmail.domain.mail.spi.OutboundMailSubmitter;
import com.sealmail.domain.mail.spi.MailSampleStore;
import com.sealmail.domain.mail.spi.SmtpRelayProbe;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.CryptoProfileSelector;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.domain.system.SystemSettingsProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MailTestUseCase {

    private final OutboundMailSubmitter outboundMailSubmitter;
    private final CertificateRepository certificateRepository;
    private final SystemSettingsProvider systemSettingsProvider;
    private final SmtpRelayProbe smtpRelayProbe;
    private final MailSampleStore mailSampleStore;
    private final MailMessageComposer mailMessageComposer;
    private final CryptoProfileSelector cryptoProfileSelector;

    public MailTestUseCase(OutboundMailSubmitter outboundMailSubmitter,
                           CertificateRepository certificateRepository,
                           SystemSettingsProvider systemSettingsProvider,
                           SmtpRelayProbe smtpRelayProbe,
                           MailSampleStore mailSampleStore,
                           MailMessageComposer mailMessageComposer,
                           CryptoProfileSelector cryptoProfileSelector) {
        this.outboundMailSubmitter = outboundMailSubmitter;
        this.certificateRepository = certificateRepository;
        this.systemSettingsProvider = systemSettingsProvider;
        this.smtpRelayProbe = smtpRelayProbe;
        this.mailSampleStore = mailSampleStore;
        this.mailMessageComposer = mailMessageComposer;
        this.cryptoProfileSelector = cryptoProfileSelector;
    }

    @Transactional(readOnly = true)
    public String sendPlain(SendMailRequest request, UserContext user) {
        requireAdmin(user);
        try {
            OutboundMailSubmitter.PlainOutboundMailSubmission submission = plainSubmission(request);
            outboundMailSubmitter.submitPlain(submission);
            return "邮件已提交发送，请查看日志和收件箱";
        } catch (Exception e) {
            throw BusinessException.badRequest("邮件发送失败: " + e.getMessage());
        }
    }

    private OutboundMailSubmitter.PlainOutboundMailSubmission plainSubmission(SendMailRequest request) {
        byte[] mailContent = mailMessageComposer.composeText(draft(request));
        return new OutboundMailSubmitter.PlainOutboundMailSubmission(
                    mailContent,
                    new EmailAddress(request.from()),
                    request.to().stream().map(EmailAddress::new).toList()
        );
    }

    @Transactional(readOnly = true)
    public String sendProtected(SendMailRequest request, UserContext user) {
        requireAdmin(user);
        try {
            byte[] mailContent = mailMessageComposer.composeText(draft(request));

            EmailAddress senderAddr = new EmailAddress(request.from());
            List<EmailAddress> recipientAddrs = request.to().stream()
                    .map(EmailAddress::new)
                    .toList();

            MailEnvelope envelope = new MailEnvelope(
                    "<" + java.util.UUID.randomUUID() + "@sealmail.local>",
                    senderAddr,
                    recipientAddrs,
                    "127.0.0.1",
                    "submission",
                    java.time.Instant.now()
            );

            CryptoProfile profile = parseProfile(request.preferredAlgorithm());
            List<Certificate> senderCerts = certificateRepository.findTrustedForSigning(senderAddr);
            Certificate selectedSenderCert = cryptoProfileSelector.select(senderCerts, profile).orElse(null);

            Map<EmailAddress, String> certMap = new HashMap<>();
            for (EmailAddress recipient : recipientAddrs) {
                List<Certificate> certs = certificateRepository.findTrustedForEncryption(recipient);
                Certificate selected = cryptoProfileSelector.select(certs, profile).orElse(null);
                if (selected != null) {
                    certMap.put(recipient, selected.getPemContent());
                }
            }
            if (certMap.size() != recipientAddrs.size()) {
                List<String> missingRecipients = recipientAddrs.stream()
                        .filter(recipient -> !certMap.containsKey(recipient))
                        .map(EmailAddress::getValue)
                        .toList();
                throw BusinessException.badRequest("以下收件人没有可用加密证书，已拒绝提交: "
                        + String.join(", ", missingRecipients));
            }

            outboundMailSubmitter.submitProtected(new OutboundMailSubmitter.ProtectedOutboundMailSubmission(
                    mailContent,
                    envelope,
                    true,
                    true,
                    profile,
                    selectedSenderCert == null ? null : selectedSenderCert.getPemContent(),
                    selectedSenderCert == null ? null : selectedSenderCert.getId().getThumbprint(),
                    certMap
            ));

            return "加密邮件已提交发送 (密码Profile: " + profile.name() + ")，签名证书数: "
                    + senderCerts.size() + ", 加密证书数: " + certMap.size();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.badRequest("邮件发送失败: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public String testSmtpConfig(UserContext user) {
        requireAdmin(user);
        SystemSettingsProvider.SystemSettingsSnapshot settings = systemSettingsProvider.snapshot();
        StringBuilder result = new StringBuilder();
        if (settings.delivery().postfix().enabled()) {
            appendProbeResult(result, "after-filter", new SmtpRelayProbe.SmtpConnectionSettings(
                    settings.delivery().postfix().host(),
                    settings.delivery().postfix().afterFilterPort(),
                    settings.delivery().postfix().useTls(),
                    "",
                    "",
                    settings.delivery().postfix().timeoutMs()
            ));
            result.append("\n");
            appendProbeResult(result, "outbound", new SmtpRelayProbe.SmtpConnectionSettings(
                    settings.delivery().postfix().host(),
                    settings.delivery().postfix().outboundPort(),
                    settings.delivery().postfix().useTls(),
                    "",
                    "",
                    settings.delivery().postfix().timeoutMs()
            ));
        } else {
            SystemSettingsProvider.RelaySettings relay = settings.delivery().directRelay();
            appendProbeResult(result, "direct-relay", new SmtpRelayProbe.SmtpConnectionSettings(
                    relay.host(),
                    relay.port(),
                    relay.useTls(),
                    relay.username(),
                    relay.password(),
                    relay.timeoutMs()
            ));
        }
        return "SMTP配置测试结果:\n" + result;
    }

    public String saveMimeMessage(SendMailRequest request, UserContext user) {
        requireAdmin(user);
        try {
            byte[] mailContent = mailMessageComposer.composeText(draft(request));
            String hint = "encrypted_email_" + System.currentTimeMillis();
            return "原始邮件已保存到: " + mailSampleStore.store(mailContent, hint).location()
                    + "\n注意: 此接口仅保存原始邮件，不进行签名和加密处理。"
                    + "\n请使用 send-encrypted 接口并检查日志验证 S/MIME 功能。";
        } catch (Exception e) {
            throw BusinessException.badRequest("操作失败: " + e.getMessage());
        }
    }

    private MailMessageComposer.MailDraft draft(SendMailRequest request) {
        return new MailMessageComposer.MailDraft(
                new EmailAddress(request.from()),
                request.to().stream().map(EmailAddress::new).toList(),
                request.subject(),
                request.content());
    }

    private CryptoProfile parseProfile(String preferredAlgorithm) {
        if (preferredAlgorithm == null || preferredAlgorithm.isBlank()) {
            return CryptoProfile.AUTO;
        }
        return CryptoProfile.fromPreferredAlgorithm(PreferredAlgorithm.valueOf(preferredAlgorithm));
    }

    private void requireAdmin(UserContext user) {
        if (user == null || !user.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以使用邮件测试工具");
        }
    }

    private void appendProbeResult(StringBuilder result, String label, SmtpRelayProbe.SmtpConnectionSettings connection) {
        result.append(label).append(": ");
        try {
            result.append(smtpRelayProbe.probe(connection).summary());
        } catch (Exception e) {
            result.append("FAILED: ")
                    .append(connection.host()).append(":").append(connection.port())
                    .append(" - ").append(e.getMessage());
        }
    }
}
