package com.sealmail.web.controller.v1;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.PostfixProperties;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.infra.crypto.util.PemUtils;
import com.sealmail.infra.mail.relay.SmtpRelayClient;
import com.sealmail.infra.mail.relay.SmtpRelayConnectionSettings;
import com.sealmail.infra.mail.OutboundMailGateway;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mail-test")
@RequiredArgsConstructor
@Tag(name = "邮件发送测试", description = "测试邮件发送功能")
public class MailTestController {

    private final OutboundMailGateway outboundMailGateway;
    private final MessageChannel mailOutboundChannel;
    private final CertificateRepository certificateRepository;
    private final RelayProperties relayProperties;
    private final PostfixProperties postfixProperties;
    private final SmtpRelayClient smtpRelayClient;

    @Data
    public static class SendMailRequest {
        @NotBlank(message = "发件人不能为空")
        private String from;

        @NotEmpty(message = "收件人不能为空")
        private List<String> to;

        @NotBlank(message = "主题不能为空")
        private String subject;

        @NotBlank(message = "邮件内容不能为空")
        private String content;

        private String preferredAlgorithm;
    }

    @PostMapping("/send")
    @Operation(summary = "发送测试邮件", description = "构造一封简单邮件并通过SMTP中继发送")
    public ApiResponse<String> sendTestMail(@Valid @RequestBody SendMailRequest request,
                                            @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        try {
            String mimeMessage = buildMimeMessage(request);
            byte[] mailContent = mimeMessage.getBytes("UTF-8");

            outboundMailGateway.submitMail(mailContent, request.getFrom(), request.getTo());

            return ApiResponse.ok("邮件已提交发送，请查看日志和收件箱");
        } catch (Exception e) {
            return ApiResponse.error(500, "邮件发送失败: " + e.getMessage());
        }
    }

    private String buildMimeMessage(SendMailRequest request) {
        StringBuilder sb = new StringBuilder();
        String messageId = "<" + java.util.UUID.randomUUID() + "@sealmail.local>";
        String date = java.time.ZonedDateTime.now()
                .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);

        // 编码中文邮件头 (RFC 2047)
        String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(request.getSubject().getBytes(StandardCharsets.UTF_8)) + "?=";

        sb.append("From: ").append(request.getFrom()).append("\r\n");
        sb.append("To: ").append(String.join(", ", request.getTo())).append("\r\n");
        sb.append("Subject: ").append(encodedSubject).append("\r\n");
        sb.append("Date: ").append(date).append("\r\n");
        sb.append("Message-ID: ").append(messageId).append("\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
        sb.append("Content-Transfer-Encoding: base64\r\n");
        sb.append("\r\n");
        sb.append(Base64.getMimeEncoder().encodeToString(request.getContent().getBytes(StandardCharsets.UTF_8)));
        sb.append("\r\n");

        return sb.toString();
    }

    @PostMapping("/send-encrypted")
    @Operation(summary = "发送加密签名邮件", description = "强制启用S/MIME签名和加密发送测试邮件，支持算法偏好测试")
    public ApiResponse<String> sendEncryptedMail(@Valid @RequestBody SendMailRequest request,
                                                 @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        try {
            String mimeMessage = buildMimeMessage(request);
            byte[] mailContent = mimeMessage.getBytes("UTF-8");

            EmailAddress senderAddr = new EmailAddress(request.getFrom());
            List<EmailAddress> recipientAddrs = request.getTo().stream()
                    .map(EmailAddress::new)
                    .toList();

            com.sealmail.domain.mailsecurity.MailEnvelope envelope =
                new com.sealmail.domain.mailsecurity.MailEnvelope(
                    "<" + java.util.UUID.randomUUID() + "@sealmail.local>",
                    senderAddr,
                    recipientAddrs,
                    "127.0.0.1",
                    "submission",
                    java.time.Instant.now()
                );

            PreferredAlgorithm preference = PreferredAlgorithm.AUTO;
            if (request.getPreferredAlgorithm() != null && !request.getPreferredAlgorithm().isBlank()) {
                preference = PreferredAlgorithm.valueOf(request.getPreferredAlgorithm());
            }

            Map<String, Object> headers = new HashMap<>();
            headers.put("mailEnvelope", envelope);
            headers.put("submissionType", "api");
            headers.put("signingEnabled", true);
            headers.put("encryptionEnabled", true);
            headers.put("preferredAlgorithm", preference.name());

            // Select sender certificate according to algorithm preference.
            // Signing step resolves the private key by thumbprint/keystore/resource fallback.
            List<Certificate> senderCerts = certificateRepository.findTrustedForSigning(senderAddr);
            Certificate selectedSenderCert = selectCertByPreference(senderCerts, preference);

            if (selectedSenderCert != null) {
                headers.put("senderCertificate", selectedSenderCert.getPemContent());
                headers.put("senderCertificateThumbprint", selectedSenderCert.getId().getThumbprint());
            } else {
                // Fallback to resource file test certificate
                loadFallbackSenderCert(headers);
            }

            // Select recipient certificates according to algorithm preference
            Map<EmailAddress, String> certMap = new HashMap<>();
            for (EmailAddress recipient : recipientAddrs) {
                List<Certificate> certs = certificateRepository.findTrustedForEncryption(recipient);
                Certificate selected = selectCertByPreference(certs, preference);
                if (selected != null) {
                    certMap.put(recipient, selected.getPemContent());
                }
            }

            // Fallback to resource file if no certs in DB
            if (certMap.isEmpty()) {
                try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("certs/recipient_sm2_cert.pem")) {
                    if (is != null) {
                        String recipientCert = new String(is.readAllBytes());
                        for (EmailAddress recipient : recipientAddrs) {
                            certMap.put(recipient, recipientCert);
                        }
                    }
                }
            }
            headers.put("recipientCertificates", certMap);

            mailOutboundChannel.send(MessageBuilder
                    .withPayload(mailContent)
                    .copyHeaders(headers)
                    .build());

            return ApiResponse.ok("加密邮件已提交发送 (算法偏好: " + preference.name() + ")，签名证书数: " +
                senderCerts.size() + ", 加密证书数: " + certMap.size());
        } catch (Exception e) {
            return ApiResponse.error(500, "邮件发送失败: " + e.getMessage());
        }
    }

    private Certificate selectCertByPreference(List<Certificate> certs, PreferredAlgorithm preference) {
        if (certs == null || certs.isEmpty()) {
            return null;
        }
        if (preference == PreferredAlgorithm.GM_ONLY) {
            for (Certificate cert : certs) {
                if (isGmCert(cert)) {
                    return cert;
                }
            }
            return null;
        }
        if (preference == PreferredAlgorithm.STANDARD_ONLY) {
            for (Certificate cert : certs) {
                if (isRsaCert(cert)) {
                    return cert;
                }
            }
            return null;
        }
        // AUTO: prefer EC/SM2, fallback to RSA
        for (Certificate cert : certs) {
            if (isGmCert(cert)) {
                return cert;
            }
        }
        return certs.get(0);
    }

    private String getCertAlgorithm(Certificate cert) {
        if (cert.getAlgorithm() != null && !cert.getAlgorithm().isBlank()) {
            return cert.getAlgorithm();
        }
        try {
            return PemUtils.parseCertificate(cert.getPemContent()).getPublicKey().getAlgorithm();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private boolean isGmCert(Certificate cert) {
        String alg = getCertAlgorithm(cert);
        return "EC".equals(alg) || "ECDSA".equals(alg) || "SM2".equals(alg);
    }

    private boolean isRsaCert(Certificate cert) {
        return "RSA".equals(getCertAlgorithm(cert));
    }

    private void loadFallbackSenderCert(Map<String, Object> headers) {
        try (java.io.InputStream certIs = getClass().getClassLoader().getResourceAsStream("certs/sender_sm2_cert.pem")) {
            if (certIs != null) {
                headers.put("senderCertificate", new String(certIs.readAllBytes()));
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @PostMapping("/test-encrypted-to-file")
    @Operation(summary = "生成加密邮件并保存到文件", description = "生成S/MIME加密签名邮件并保存到本地文件用于验证")
    public ApiResponse<String> testEncryptedToFile(@Valid @RequestBody SendMailRequest request,
                                                   @AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        try {
            String mimeMessage = buildMimeMessage(request);
            byte[] mailContent = mimeMessage.getBytes("UTF-8");

            String filename = "/tmp/encrypted_email_" + System.currentTimeMillis() + ".eml";
            java.nio.file.Files.write(java.nio.file.Paths.get(filename), mailContent);

            return ApiResponse.ok("原始邮件已保存到: " + filename +
                "\n注意: 此接口仅保存原始邮件，不进行签名和加密处理。" +
                "\n请使用 send-encrypted 接口并检查日志验证 S/MIME 功能。");
        } catch (Exception e) {
            return ApiResponse.error(500, "操作失败: " + e.getMessage());
        }
    }

    @GetMapping("/test-smtp-config")
    @Operation(summary = "测试SMTP配置", description = "测试SMTP配置连接")
    public ApiResponse<String> testSmtpConfig(@AuthenticationPrincipal UserContext user) {
        requireAdmin(user);
        StringBuilder result = new StringBuilder();
        if (postfixProperties.isEnabled()) {
            appendProbeResult(result, "after-filter", new SmtpRelayConnectionSettings(
                    postfixProperties.getHost(),
                    postfixProperties.getAfterFilterPort(),
                    postfixProperties.isUseTls(),
                    "",
                    "",
                    postfixProperties.getTimeout()
            ));
            result.append("\n");
            appendProbeResult(result, "outbound", new SmtpRelayConnectionSettings(
                    postfixProperties.getHost(),
                    postfixProperties.getOutboundPort(),
                    postfixProperties.isUseTls(),
                    "",
                    "",
                    postfixProperties.getTimeout()
            ));
        } else {
            appendProbeResult(result, "direct-relay", new SmtpRelayConnectionSettings(
                    relayProperties.getHost(),
                    relayProperties.getPort(),
                    relayProperties.isUseTls(),
                    relayProperties.getUsername(),
                    relayProperties.getPassword(),
                    relayProperties.getTimeout()
            ));
        }
        return ApiResponse.ok("SMTP配置测试结果:\n" + result);
    }

    private void requireAdmin(UserContext user) {
        if (user == null || !user.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以使用邮件测试工具");
        }
    }

    private void appendProbeResult(StringBuilder result, String label, SmtpRelayConnectionSettings connection) {
        result.append(label).append(": ");
        try {
            result.append(smtpRelayClient.probe(connection).summary());
        } catch (Exception e) {
            result.append("FAILED: ")
                    .append(connection.host()).append(":").append(connection.port())
                    .append(" - ").append(e.getMessage());
        }
    }
}
