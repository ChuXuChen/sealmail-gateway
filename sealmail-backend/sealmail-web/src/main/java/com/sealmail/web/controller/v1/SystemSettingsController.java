package com.sealmail.web.controller.v1;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.infra.config.properties.CaProperties;
import com.sealmail.infra.config.properties.PostfixProperties;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.infra.config.properties.SecurityProperties;
import com.sealmail.infra.config.properties.SmtpServerProperties;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system-settings")
@RequiredArgsConstructor
@Tag(name = "系统设置", description = "只读运行配置与运维状态")
public class SystemSettingsController {

    private final Environment environment;
    private final SmtpServerProperties smtpServerProperties;
    private final PostfixProperties postfixProperties;
    private final RelayProperties relayProperties;
    private final SecurityProperties securityProperties;
    private final CaProperties caProperties;

    @GetMapping
    @Operation(summary = "查询系统设置快照", description = "返回脱敏后的运行配置。启动参数不支持通过 API 在线修改。")
    public ApiResponse<SystemSettingsResponse> getSettings(@AuthenticationPrincipal UserContext user) {
        requireAdmin(user);

        return ApiResponse.ok(new SystemSettingsResponse(
                runtime(),
                smtpServer(),
                delivery(),
                certificateValidation(),
                internalCa(),
                cryptoCapabilities()
        ));
    }

    private RuntimeResponse runtime() {
        return new RuntimeResponse(
                environment.getProperty("spring.application.name", "sealmail-gateway"),
                List.of(environment.getActiveProfiles()),
                false,
                "application.yml / environment variables",
                Instant.now()
        );
    }

    private SmtpServerResponse smtpServer() {
        return new SmtpServerResponse(
                smtpServerProperties.getBindAddress(),
                smtpServerProperties.getPort(),
                smtpServerProperties.getMaxConnections(),
                smtpServerProperties.getMaxMessageSize(),
                new TlsResponse(
                        smtpServerProperties.isEnableStartTls(),
                        smtpServerProperties.isRequireTls(),
                        hasText(smtpServerProperties.getKeystorePath()),
                        hasText(smtpServerProperties.getCertificatePath()) && hasText(smtpServerProperties.getPrivateKeyPath()),
                        emptyToNull(smtpServerProperties.getKeyAlias())
                )
        );
    }

    private DeliveryResponse delivery() {
        return new DeliveryResponse(
                postfixProperties.isEnabled() ? "POSTFIX" : "DIRECT_RELAY",
                new PostfixResponse(
                        postfixProperties.isEnabled(),
                        postfixProperties.getHost(),
                        postfixProperties.getAfterFilterPort(),
                        postfixProperties.getOutboundPort(),
                        postfixProperties.isUseTls(),
                        postfixProperties.getTimeout(),
                        emptyToNull(postfixProperties.getEnvelopeFrom())
                ),
                new RelayResponse(
                        relayProperties.getHost(),
                        relayProperties.getPort(),
                        relayProperties.isUseTls(),
                        relayProperties.getTimeout(),
                        hasText(relayProperties.getUsername()),
                        hasText(relayProperties.getPassword())
                )
        );
    }

    private CertificateValidationResponse certificateValidation() {
        return new CertificateValidationResponse(
                securityProperties.isCrlEnabled(),
                securityProperties.isOcspEnabled(),
                securityProperties.getOcspTimeout()
        );
    }

    private InternalCaResponse internalCa() {
        return new InternalCaResponse(
                caProperties.getCrlBaseUrl(),
                caProperties.getDefaultRootValidityDays(),
                caProperties.getDefaultIntermediateValidityDays(),
                caProperties.getDefaultEndEntityValidityDays()
        );
    }

    private List<CryptoCapabilityResponse> cryptoCapabilities() {
        return List.of(
                new CryptoCapabilityResponse("签名算法", List.of("SM3withSM2", "SHA256withRSA")),
                new CryptoCapabilityResponse("内容加密", List.of("SM4-CBC", "AES-256-CBC")),
                new CryptoCapabilityResponse("密钥交换", List.of("SM2 KeyAgreement", "RSA KeyTransport")),
                new CryptoCapabilityResponse("哈希算法", List.of("SM3", "SHA-256"))
        );
    }

    private void requireAdmin(UserContext user) {
        if (user == null || !user.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以查看系统设置");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String emptyToNull(String value) {
        return hasText(value) ? value : null;
    }

    public record SystemSettingsResponse(
            RuntimeResponse runtime,
            SmtpServerResponse smtpServer,
            DeliveryResponse delivery,
            CertificateValidationResponse certificateValidation,
            InternalCaResponse internalCa,
            List<CryptoCapabilityResponse> cryptoCapabilities
    ) {
    }

    public record RuntimeResponse(
            String applicationName,
            List<String> activeProfiles,
            boolean onlineEditingSupported,
            String configSource,
            Instant generatedAt
    ) {
    }

    public record SmtpServerResponse(
            String bindAddress,
            int port,
            int maxConnections,
            int maxMessageSizeBytes,
            TlsResponse tls
    ) {
    }

    public record TlsResponse(
            boolean startTlsEnabled,
            boolean tlsRequired,
            boolean keystoreConfigured,
            boolean pemConfigured,
            String keyAlias
    ) {
    }

    public record DeliveryResponse(
            String mode,
            PostfixResponse postfix,
            RelayResponse directRelay
    ) {
    }

    public record PostfixResponse(
            boolean enabled,
            String host,
            int afterFilterPort,
            int outboundPort,
            boolean useTls,
            int timeoutMs,
            String envelopeFrom
    ) {
    }

    public record RelayResponse(
            String host,
            int port,
            boolean useTls,
            int timeoutMs,
            boolean usernameConfigured,
            boolean passwordConfigured
    ) {
    }

    public record CertificateValidationResponse(
            boolean crlEnabled,
            boolean ocspEnabled,
            int ocspTimeoutMs
    ) {
    }

    public record InternalCaResponse(
            String crlBaseUrl,
            int defaultRootValidityDays,
            int defaultIntermediateValidityDays,
            int defaultEndEntityValidityDays
    ) {
    }

    public record CryptoCapabilityResponse(
            String category,
            List<String> algorithms
    ) {
    }
}
