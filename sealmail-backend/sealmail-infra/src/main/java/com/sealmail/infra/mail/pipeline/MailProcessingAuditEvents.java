package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.mailsecurity.AuditTrace;
import com.sealmail.domain.mailsecurity.CertificateSelection;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.RelayProfile;
import com.sealmail.domain.mailsecurity.RoutingDecision;
import com.sealmail.domain.shared.event.AuditEvent;
import com.sealmail.infra.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class MailProcessingAuditEvents {

    public static final String RESOURCE_TYPE = "MAIL_PROCESSING";

    private static final Logger log = LoggerFactory.getLogger(MailProcessingAuditEvents.class);
    private static final int MAX_DETAIL_LENGTH = 1500;

    private MailProcessingAuditEvents() {
    }

    public static void publish(DomainEventPublisher publisher,
                               AuditLogType type,
                               MailProcessingContext context,
                               String action,
                               String detail) {
        publish(publisher, type, context, action, detail, true);
    }

    public static void publish(DomainEventPublisher publisher,
                               AuditLogType type,
                               MailProcessingContext context,
                               String action,
                               String detail,
                               boolean success) {
        if (publisher == null || type == null) {
            return;
        }
        try {
            publisher.publishEvent(AuditEvent.builder()
                    .eventType(type.name())
                    .resourceType(RESOURCE_TYPE)
                    .resourceId(resourceId(context))
                    .action(action != null && !action.isBlank() ? action : type.name())
                    .description(description(context, detail))
                    .success(success)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish mail processing audit event {}: {}", type, e.getMessage());
        }
    }

    public static String routeDecisionSummary(RoutingDecision decision) {
        if (decision == null) {
            return "decision=UNKNOWN";
        }
        if (decision instanceof RoutingDecision.OutboundEncrypt outboundEncrypt) {
            return "decision=OUTBOUND_ENCRYPT, recipientCount=" + outboundEncrypt.getRecipients().size();
        }
        if (decision instanceof RoutingDecision.OutboundSign outboundSign) {
            return "decision=OUTBOUND_SIGN, recipientCount=" + outboundSign.getRecipients().size();
        }
        if (decision instanceof RoutingDecision.InboundDecrypt) {
            return "decision=INBOUND_DECRYPT";
        }
        if (decision instanceof RoutingDecision.InboundVerify) {
            return "decision=INBOUND_VERIFY";
        }
        if (decision instanceof RoutingDecision.PassThrough) {
            return "decision=PASS_THROUGH";
        }
        if (decision instanceof RoutingDecision.Quarantine quarantine) {
            return "decision=QUARANTINE, reason=" + quarantine.getReason()
                    + detailPresence(quarantine.getDetail());
        }
        return "decision=" + decision.getClass().getSimpleName();
    }

    public static String certificateSelectionSummary(CertificateSelection selection) {
        if (selection == null) {
            return "senderCertificateSelected=false, recipientCertificateCount=0";
        }
        String senderThumbprint = selection.senderCertificateThumbprint();
        List<String> recipientThumbprints = selection.recipientCertificateThumbprints().entrySet().stream()
                .map(entry -> entry.getKey().getValue() + ":" + entry.getValue())
                .toList();
        return "senderCertificateSelected=" + hasText(selection.senderCertificatePem())
                + safeSuffix(", senderThumbprint=", senderThumbprint)
                + ", recipientCertificateCount=" + selection.recipientCertificates().size()
                + ", recipientThumbprints=" + truncate(String.join(",", recipientThumbprints), 512);
    }

    public static String relayProfileSummary(RelayProfile relayProfile) {
        if (relayProfile == null) {
            return "relayConfigured=false";
        }
        return "relayConfigured=true"
                + ", host=" + safe(relayProfile.host())
                + ", port=" + relayProfile.port()
                + ", transportProfile=" + relayProfile.transportProfile()
                + ", usernameConfigured=" + hasText(relayProfile.username());
    }

    public static String detailPresence(String value) {
        return ", detailPresent=" + hasText(value)
                + ", detailLength=" + (value != null ? value.length() : 0);
    }

    public static String safeAuditDetail(String value) {
        String sanitized = safe(value);
        sanitized = sanitized.replaceAll(
                "-----BEGIN [^-]+-----.*?-----END [^-]+-----",
                "<redacted-pem>");
        return sanitized;
    }

    private static String description(MailProcessingContext context, String detail) {
        StringBuilder builder = new StringBuilder();
        builder.append("processingId=").append(safe(processingId(context)));
        builder.append(", correlationId=").append(safe(correlationId(context)));
        builder.append(", messageId=").append(safe(messageId(context)));
        if (context != null && context.direction() != null) {
            builder.append(", direction=").append(context.direction());
        }
        if (detail != null && !detail.isBlank()) {
            builder.append(", detail=").append(safeAuditDetail(detail));
        }
        return truncate(builder.toString(), MAX_DETAIL_LENGTH);
    }

    private static String resourceId(MailProcessingContext context) {
        String processingId = processingId(context);
        if (hasText(processingId)) {
            return processingId;
        }
        return messageId(context);
    }

    private static String processingId(MailProcessingContext context) {
        return context != null ? context.processingId() : null;
    }

    private static String correlationId(MailProcessingContext context) {
        AuditTrace trace = context != null ? context.auditTrace() : null;
        return trace != null ? trace.correlationId() : null;
    }

    private static String messageId(MailProcessingContext context) {
        if (context == null) {
            return null;
        }
        AuditTrace trace = context.auditTrace();
        if (trace != null && hasText(trace.messageId())) {
            return trace.messageId();
        }
        return context.envelope() != null ? context.envelope().getMessageId() : null;
    }

    private static String safeSuffix(String label, String value) {
        return hasText(value) ? label + safe(value) : "";
    }

    private static String safe(String value) {
        if (value == null) {
            return "unknown";
        }
        String sanitized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return redactSecrets(sanitized);
    }

    private static String redactSecrets(String value) {
        return value
                .replaceAll("(?i)(privateKey|password|secret|token)\\s*[=:]\\s*([^,;\\s]+)", "$1=<redacted>")
                .replaceAll("(?i)(privateKey|password|secret|token)\\s+[\"']([^\"']+)[\"']", "$1 <redacted>");
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
