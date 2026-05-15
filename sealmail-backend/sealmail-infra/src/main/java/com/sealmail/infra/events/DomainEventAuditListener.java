package com.sealmail.infra.events;

import com.sealmail.domain.audit.AuditLog;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.certificate.event.CertificateDeleted;
import com.sealmail.domain.certificate.event.CertificateImported;
import com.sealmail.domain.certificate.event.CertificateIssued;
import com.sealmail.domain.certificate.event.CertificateRevoked;
import com.sealmail.domain.certificate.event.CertificateTrusted;
import com.sealmail.domain.certificate.event.CertificateUntrusted;
import com.sealmail.domain.exceptionmail.event.ExceptionMailCreated;
import com.sealmail.domain.mailsecurity.event.MailDecrypted;
import com.sealmail.domain.mailsecurity.event.MailDelivered;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.mailsecurity.event.MailQuarantined;
import com.sealmail.domain.mailsecurity.event.MailReceived;
import com.sealmail.domain.mailsecurity.event.MailSigned;
import com.sealmail.domain.mailsecurity.event.MailVerified;
import com.sealmail.domain.policy.event.DkimSettingChanged;
import com.sealmail.domain.policy.event.DlpPatternConfigChanged;
import com.sealmail.domain.policy.event.DlpSelectionConfigChanged;
import com.sealmail.domain.policy.event.DomainConfigActivationChanged;
import com.sealmail.domain.policy.event.DomainConfigCreated;
import com.sealmail.domain.policy.event.DomainConfigDeleted;
import com.sealmail.domain.policy.event.EncryptionPolicyChanged;
import com.sealmail.domain.policy.event.MailAuthConfigChanged;
import com.sealmail.domain.policy.event.PreferredAlgorithmChanged;
import com.sealmail.domain.policy.event.SigningDisabled;
import com.sealmail.domain.policy.event.SigningEnabled;
import com.sealmail.domain.quarantine.event.QuarantineCreated;
import com.sealmail.domain.quarantine.event.QuarantineRejected;
import com.sealmail.domain.quarantine.event.QuarantineReleased;
import com.sealmail.domain.shared.event.AuditEvent;
import com.sealmail.domain.shared.event.DomainEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
public class DomainEventAuditListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventAuditListener.class);

    private final AuditLogRepository auditLogRepository;

    public DomainEventAuditListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDomainEvent(DomainEvent event) {
        AuditLog auditLog = toAuditLog(event);
        if (auditLog == null) {
            return;
        }

        try {
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for {}: {}",
                    event.getClass().getSimpleName(), e.getMessage());
        }
    }

    private AuditLog toAuditLog(DomainEvent event) {
        Actor actor = resolveActor();
        String ipAddress = resolveIpAddress();

        if (event instanceof CertificateIssued certificateIssued) {
            return audit(
                    AuditLogType.CERTIFICATE_ISSUED,
                    actor,
                    ipAddress,
                    "CERTIFICATE",
                    certificateIssued.getCertificateId().getThumbprint(),
                    "签发证书给 " + certificateIssued.getOwner().getValue());
        }
        if (event instanceof CertificateImported certificateImported) {
            return audit(
                    AuditLogType.CERTIFICATE_IMPORTED,
                    actor,
                    ipAddress,
                    "CERTIFICATE",
                    certificateImported.getCertificateId().getThumbprint(),
                    "导入证书，所有者 " + certificateImported.getOwner().getValue());
        }
        if (event instanceof CertificateTrusted certificateTrusted) {
            return audit(
                    AuditLogType.CERTIFICATE_TRUSTED,
                    actor,
                    ipAddress,
                    "CERTIFICATE",
                    certificateTrusted.getCertificateId().getThumbprint(),
                    "信任证书");
        }
        if (event instanceof CertificateUntrusted certificateUntrusted) {
            return audit(
                    AuditLogType.CERTIFICATE_UNTRUSTED,
                    actor,
                    ipAddress,
                    "CERTIFICATE",
                    certificateUntrusted.getCertificateId().getThumbprint(),
                    "撤销证书信任");
        }
        if (event instanceof CertificateRevoked certificateRevoked) {
            return audit(
                    AuditLogType.CERTIFICATE_REVOKED,
                    actor,
                    ipAddress,
                    "CERTIFICATE",
                    certificateRevoked.getCertificateId().getThumbprint(),
                    "吊销证书: " + certificateRevoked.getReason());
        }
        if (event instanceof CertificateDeleted certificateDeleted) {
            return audit(
                    AuditLogType.CERTIFICATE_DELETED,
                    actor,
                    ipAddress,
                    "CERTIFICATE",
                    certificateDeleted.getCertificateId().getThumbprint(),
                    "删除证书，所有者 " + certificateDeleted.getOwner().getValue());
        }

        if (event instanceof QuarantineCreated quarantineCreated) {
            return audit(
                    AuditLogType.EMAIL_QUARANTINED,
                    actor,
                    ipAddress,
                    "EMAIL",
                    quarantineCreated.getMessageId(),
                    "隔离邮件: " + quarantineCreated.getReason());
        }
        if (event instanceof ExceptionMailCreated exceptionMailCreated) {
            return audit(
                    AuditLogType.EMAIL_QUARANTINED,
                    actor,
                    ipAddress,
                    "EMAIL",
                    exceptionMailCreated.getMessageId(),
                    "异常邮件: " + exceptionMailCreated.getReason()
                            + detailSuffix(exceptionMailCreated.getDetail()));
        }
        if (event instanceof QuarantineReleased quarantineReleased) {
            return audit(
                    AuditLogType.EMAIL_RELEASED,
                    actorFor(quarantineReleased.getReleasedBy(), actor),
                    ipAddress,
                    "QUARANTINE",
                    quarantineReleased.getQuarantineId(),
                    quarantineReleased.getComment());
        }
        if (event instanceof QuarantineRejected quarantineRejected) {
            return audit(
                    AuditLogType.EMAIL_REJECTED,
                    actorFor(quarantineRejected.getRejectedBy(), actor),
                    ipAddress,
                    "QUARANTINE",
                    quarantineRejected.getQuarantineId(),
                    quarantineRejected.getComment());
        }

        if (event instanceof MailReceived mailReceived) {
            return audit(
                    AuditLogType.EMAIL_RECEIVED,
                    actor,
                    ipAddress,
                    "EMAIL",
                    mailReceived.getMessageId(),
                    "接收邮件: " + mailReceived.getDirection());
        }
        if (event instanceof MailQuarantined mailQuarantined) {
            return audit(
                    AuditLogType.EMAIL_QUARANTINED,
                    actor,
                    ipAddress,
                    "EMAIL",
                    mailQuarantined.getMessageId(),
                    "隔离邮件: " + mailQuarantined.getReason()
                            + detailSuffix(mailQuarantined.getDetail()));
        }
        if (event instanceof MailEncrypted mailEncrypted) {
            return audit(
                    AuditLogType.EMAIL_ENCRYPTED,
                    actor,
                    ipAddress,
                    "EMAIL",
                    mailEncrypted.getMessageId(),
                    "加密邮件给 " + mailEncrypted.getRecipient().getValue()
                            + "，证书 " + mailEncrypted.getCertificateId().getThumbprint());
        }
        if (event instanceof MailSigned mailSigned) {
            return audit(
                    AuditLogType.EMAIL_SIGNED,
                    actor,
                    ipAddress,
                    "EMAIL",
                    mailSigned.getMessageId(),
                    "签名邮件，发件人 " + mailSigned.getSender().getValue()
                            + "，证书 " + mailSigned.getCertificateId().getThumbprint());
        }
        if (event instanceof MailDecrypted mailDecrypted) {
            return audit(
                    AuditLogType.EMAIL_DECRYPTED,
                    actor,
                    ipAddress,
                    "EMAIL",
                    mailDecrypted.getMessageId(),
                    "解密邮件给 " + mailDecrypted.getRecipient().getValue());
        }
        if (event instanceof MailVerified mailVerified) {
            return audit(
                    AuditLogType.EMAIL_VERIFIED,
                    actor,
                    ipAddress,
                    "EMAIL",
                    mailVerified.getMessageId(),
                    mailVerified.isValid()
                            ? "验签通过，发件人 " + mailVerified.getSender().getValue()
                            : "验签失败，发件人 " + mailVerified.getSender().getValue());
        }
        if (event instanceof MailDelivered mailDelivered) {
            return audit(
                    AuditLogType.EMAIL_DELIVERED,
                    actor,
                    ipAddress,
                    "EMAIL",
                    mailDelivered.getMessageId(),
                    "邮件处理完成");
        }

        if (event instanceof DomainConfigCreated domainConfigCreated) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "DOMAIN_CONFIG",
                    domainConfigCreated.getConfigId(),
                    "创建域名配置: " + domainConfigCreated.getDomain());
        }
        if (event instanceof EncryptionPolicyChanged encryptionPolicyChanged) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "DOMAIN_CONFIG",
                    encryptionPolicyChanged.getConfigId(),
                    "修改加密策略: " + encryptionPolicyChanged.getOldPolicy()
                            + " -> " + encryptionPolicyChanged.getNewPolicy());
        }
        if (event instanceof PreferredAlgorithmChanged preferredAlgorithmChanged) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "DOMAIN_CONFIG",
                    preferredAlgorithmChanged.getConfigId(),
                    "修改算法偏好: " + preferredAlgorithmChanged.getOldAlgorithm()
                            + " -> " + preferredAlgorithmChanged.getNewAlgorithm());
        }
        if (event instanceof SigningEnabled signingEnabled) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "DOMAIN_CONFIG",
                    signingEnabled.getConfigId(),
                    "启用邮件签名");
        }
        if (event instanceof SigningDisabled signingDisabled) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "DOMAIN_CONFIG",
                    signingDisabled.getConfigId(),
                    "禁用邮件签名");
        }
        if (event instanceof DkimSettingChanged dkimSettingChanged) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "DOMAIN_CONFIG",
                    dkimSettingChanged.getConfigId(),
                    dkimSettingChanged.isEnabled() ? "启用 DKIM" : "禁用 DKIM");
        }
        if (event instanceof DomainConfigActivationChanged activationChanged) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "DOMAIN_CONFIG",
                    activationChanged.getConfigId(),
                    activationChanged.isActive() ? "启用域名配置" : "停用域名配置");
        }
        if (event instanceof DomainConfigDeleted domainConfigDeleted) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "DOMAIN_CONFIG",
                    domainConfigDeleted.getConfigId(),
                    "删除域名配置: " + domainConfigDeleted.getDomain());
        }
        if (event instanceof DlpPatternConfigChanged dlpPatternChanged) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "DLP_PATTERN",
                    dlpPatternChanged.getPatternId(),
                    operationText(dlpPatternChanged.getOperation()) + " DLP 规则: "
                            + dlpPatternChanged.getName());
        }
        if (event instanceof DlpSelectionConfigChanged dlpSelectionChanged) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "DLP_SELECTION",
                    dlpSelectionChanged.getSelectionId(),
                    operationText(dlpSelectionChanged.getOperation()) + " DLP 生效范围: "
                            + dlpSelectionChanged.getScopeType()
                            + scopeSuffix(dlpSelectionChanged.getScopeValue()));
        }
        if (event instanceof MailAuthConfigChanged mailAuthChanged) {
            return audit(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    actor,
                    ipAddress,
                    "MAIL_AUTH_CONFIG",
                    mailAuthChanged.getConfigId(),
                    "修改邮件认证配置: " + String.join(", ", mailAuthChanged.getChangedSections()));
        }

        if (event instanceof AuditEvent auditEvent) {
            AuditLogType type = resolveType(auditEvent.getEventType());
            return AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .type(type)
                    .userId(auditEvent.getUserId() != null ? auditEvent.getUserId() : actor.userId())
                    .username(auditEvent.getUsername() != null ? auditEvent.getUsername() : actor.username())
                    .ipAddress(auditEvent.getIpAddress() != null ? auditEvent.getIpAddress() : ipAddress)
                    .resourceType(auditEvent.getResourceType())
                    .resourceId(auditEvent.getResourceId())
                    .action(auditEvent.getAction() != null ? auditEvent.getAction() : type.name())
                    .detail(auditEvent.getDescription())
                    .success(auditEvent.isSuccess())
                    .occurredAt(auditEvent.getOccurredAt())
                    .build();
        }

        return null;
    }

    private AuditLog audit(AuditLogType type,
                           Actor actor,
                           String ipAddress,
                           String resourceType,
                           String resourceId,
                           String detail) {
        return AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .userId(actor.userId())
                .username(actor.username())
                .ipAddress(ipAddress)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .action(type.name())
                .detail(detail)
                .success(true)
                .build();
    }

    private AuditLogType resolveType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return AuditLogType.OTHER;
        }
        try {
            return AuditLogType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            return AuditLogType.OTHER;
        }
    }

    private Actor resolveActor() {
        Object principal = currentPrincipal();
        if (principal != null) {
            String userId = readStringProperty(principal, "getUserId");
            String username = readStringProperty(principal, "getUsername");
            if (userId != null || username != null) {
                return new Actor(userId, username);
            }
        }
        return new Actor("system", "system");
    }

    private Object currentPrincipal() {
        try {
            Class<?> holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = holderClass.getMethod("getContext").invoke(null);
            Object authentication = context.getClass().getMethod("getAuthentication").invoke(context);
            if (authentication == null) {
                return null;
            }
            return authentication.getClass().getMethod("getPrincipal").invoke(authentication);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readStringProperty(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Actor actorFor(String userIdOrName, Actor fallback) {
        if (userIdOrName == null || userIdOrName.isBlank()) {
            return fallback;
        }
        return new Actor(userIdOrName, userIdOrName);
    }

    private String resolveIpAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",", 2)[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

    private String detailSuffix(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        return "，" + detail;
    }

    private String operationText(String operation) {
        if ("CREATE".equals(operation)) {
            return "创建";
        }
        if ("UPDATE".equals(operation)) {
            return "更新";
        }
        if ("DELETE".equals(operation)) {
            return "删除";
        }
        return "修改";
    }

    private String scopeSuffix(String scopeValue) {
        if (scopeValue == null || scopeValue.isBlank()) {
            return "";
        }
        return " " + scopeValue;
    }

    private record Actor(String userId, String username) {
    }
}
