package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.config.RelayPolicyPort;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.mailsecurity.*;
import com.sealmail.domain.policy.DecryptionMode;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.infra.config.properties.PostfixProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RoutingService {

    private final MailRouter mailRouter;
    private final DomainRoutingPolicyResolver domainRoutingPolicyResolver;
    private final MailCryptoSelectionService cryptoSelectionService;
    private final MailProcessingRepository mailProcessingRepository;
    private final MailAuthPolicyRepository mailAuthPolicyRepository;
    private final RelayProfileResolver relayProfileResolver;
    private final RoutingAuditPublisher routingAuditPublisher;

    @Autowired
    public RoutingService(MailRouter mailRouter,
	                           DomainRoutingPolicyResolver domainRoutingPolicyResolver,
	                           MailCryptoSelectionService cryptoSelectionService,
	                           MailProcessingRepository mailProcessingRepository,
	                           MailAuthPolicyRepository mailAuthPolicyRepository,
	                           RelayProfileResolver relayProfileResolver,
	                           RoutingAuditPublisher routingAuditPublisher) {
        this.mailRouter = mailRouter;
        this.domainRoutingPolicyResolver = domainRoutingPolicyResolver;
        this.cryptoSelectionService = cryptoSelectionService;
        this.mailProcessingRepository = mailProcessingRepository;
        this.mailAuthPolicyRepository = mailAuthPolicyRepository;
        this.relayProfileResolver = relayProfileResolver;
        this.routingAuditPublisher = routingAuditPublisher;
    }

    RoutingService(MailRouter mailRouter,
                   DomainConfigRepository domainConfigRepository,
                   MailCryptoSelectionService cryptoSelectionService,
                   MailProcessingRepository mailProcessingRepository,
                   PostfixProperties postfixProperties,
                   MailAuthPolicyRepository mailAuthPolicyRepository,
                   com.sealmail.infra.events.DomainEventPublisher domainEventPublisher,
                   RelayPolicyPort relayPolicyPort,
                   DeliveryRouteResolver deliveryRouteResolver) {
        this(
                mailRouter,
                new DomainRoutingPolicyResolver(domainConfigRepository, relayPolicyPort),
                cryptoSelectionService,
                mailProcessingRepository,
                mailAuthPolicyRepository,
                new RelayProfileResolver(
                        postfixProperties,
                        deliveryRouteResolver != null
                                ? deliveryRouteResolver
                                : new DomainDeliveryRouteResolver(domainConfigRepository),
                        "127.0.0.1",
                        2526),
                new RoutingAuditPublisher(domainEventPublisher));
    }

    @Transactional
    public Message<byte[]> routeInbound(Message<byte[]> message) {
        MailProcessingContext messageContext = context(message);
        MailEnvelope envelope = messageContext != null ? messageContext.envelope() : null;
        if (envelope == null) {
            return message;
        }

        Optional<DomainConfig> domainConfig = domainRoutingPolicyResolver.firstActiveLocalRecipientDomain(envelope);
        if (domainConfig.isEmpty()) {
            return exceptionByDomainPolicy(
                    message,
                    envelope,
                    MailDirection.INBOUND,
                    "No recipient domain is configured and enabled as a local domain for this mail");
        }

        List<Certificate> certificates = cryptoSelectionService.inboundRoutingCertificates(envelope);

        MailProcessing processing = MailProcessing.create(processingId(messageContext), envelope, MailDirection.INBOUND);
        processing.addStep("routing");

        RoutingDecision decision = mailRouter.route(
                envelope,
                MailDirection.INBOUND,
                domainConfig.get(),
                certificates,
                List.of(),
                null,
                CryptoProfile.AUTO
        );

        processing.setRoutingDecision(decision);
        processing.completeStep("routing", true, null);

        mailProcessingRepository.save(processing);

        return enhanceMessageWithRouting(
                message,
                decision,
                envelope,
                domainConfig.get(),
                processing.getId(),
                MailDirection.INBOUND
        );
    }

    @Transactional
    public Message<byte[]> routeOutbound(Message<byte[]> message) {
        MailProcessingContext messageContext = context(message);
        MailEnvelope envelope = messageContext != null ? messageContext.envelope() : null;
        if (envelope == null) {
            return message;
        }

        String senderDomain = envelope.getSender().getDomain();

        Optional<DomainConfig> domainConfig = domainRoutingPolicyResolver.activeLocalDomain(senderDomain);
        if (domainConfig.isEmpty()) {
            return exceptionByDomainPolicy(
                    message,
                    envelope,
                    MailDirection.OUTBOUND,
                    "Sender domain is not configured and enabled as a local domain: " + senderDomain);
        }

        Optional<Message<byte[]>> recipientDomainFailure = rejectUnconfiguredExternalRecipients(message, envelope);
        if (recipientDomainFailure.isPresent()) {
            return recipientDomainFailure.get();
        }

        CryptoProfile requestedProfile = CryptoProfile.fromDomainConfig(domainConfig.get());
        List<Certificate> recipientCerts = cryptoSelectionService.outboundRoutingCertificates(envelope);

        MailProcessing processing = MailProcessing.create(processingId(messageContext), envelope, MailDirection.OUTBOUND);
        processing.addStep("routing");

        String content = new String(message.getPayload());

        RoutingDecision decision = mailRouter.route(
                envelope,
                MailDirection.OUTBOUND,
                domainConfig.get(),
                recipientCerts,
                List.of(),
                content,
                requestedProfile
        );

        processing.setRoutingDecision(decision);
        if (isOutboundCryptoProfileFailure(MailDirection.OUTBOUND, decision)) {
            processing.completeStep("routing", false, ((RoutingDecision.Quarantine) decision).getDetail());
        } else {
            processing.completeStep("routing", true, null);
        }

        mailProcessingRepository.save(processing);

        return enhanceMessageWithRouting(
                message,
                decision,
                envelope,
                domainConfig.get(),
                processing.getId(),
                MailDirection.OUTBOUND
        );
    }

    private Optional<Message<byte[]>> rejectUnconfiguredExternalRecipients(Message<byte[]> message,
                                                                           MailEnvelope envelope) {
        List<String> unconfiguredDomains = domainRoutingPolicyResolver.unconfiguredExternalRecipientDomains(envelope);
        if (unconfiguredDomains.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(exceptionByDomainPolicy(
                message,
                envelope,
                MailDirection.OUTBOUND,
                "Recipient domain is not configured and enabled for outbound delivery: "
                        + String.join(", ", unconfiguredDomains)));
    }

    private Message<byte[]> quarantineByRoutingPolicy(Message<byte[]> message,
                                                       MailEnvelope envelope,
                                                       MailDirection direction,
                                                       String detail) {
        return quarantineByRoutingPolicy(message, envelope, direction, QuarantineReason.POLICY_VIOLATION, detail);
    }

    private Message<byte[]> exceptionByDomainPolicy(Message<byte[]> message,
                                                    MailEnvelope envelope,
                                                    MailDirection direction,
                                                    String detail) {
        return quarantineByRoutingPolicy(message, envelope, direction, QuarantineReason.DOMAIN_NOT_CONFIGURED, detail);
    }

    private Message<byte[]> quarantineByRoutingPolicy(Message<byte[]> message,
                                                       MailEnvelope envelope,
                                                       MailDirection direction,
                                                       QuarantineReason reason,
                                                       String detail) {
        MailProcessing processing = MailProcessing.create(processingId(context(message)), envelope, direction);
        processing.addStep("routing");
        RoutingDecision.Quarantine decision = new RoutingDecision.Quarantine(reason, detail);
        processing.setRoutingDecision(decision);
        processing.completeStep("routing", false, detail);
        mailProcessingRepository.save(processing);
        return enhanceMessageWithRouting(
                message,
                decision,
                envelope,
                null,
                processing.getId(),
                direction
        );
    }

    private Message<byte[]> enhanceMessageWithRouting(Message<byte[]> message, RoutingDecision decision,
                                                       MailEnvelope envelope, DomainConfig domainConfig,
                                                       String processingId, MailDirection direction) {
        MailProcessingContext baseContext = context(message);
        if (baseContext == null) {
            baseContext = MailProcessingContext.initial(
                    envelope,
                    direction,
                    null,
                    message.getPayload(),
                    null,
                    envelope.getRemoteHost());
        }
        CryptoProfile cryptoProfile = resolveCryptoProfile(baseContext, domainConfig);
        MailProcessingDecision processingDecision = baseContext.decision();
        CertificateSelection certificates = baseContext.certificateSelection();
        RelayProfile relayProfile = relayProfileResolver.resolve(direction, envelope);

        if (isOutboundCryptoProfileFailure(direction, decision)) {
            MailProcessingContext failedContext = routedContext(
                    baseContext,
                    decision,
                    processingId,
                    direction,
                    cryptoProfile,
                    processingDecision,
                    certificates,
                    relayProfile);
            routingAuditPublisher.publishRouting(failedContext, decision);
            routingAuditPublisher.publishCertificateSelection(failedContext);
            throw new MailProcessingException(
                    MailProcessingErrorType.ENCRYPTION,
                    ((RoutingDecision.Quarantine) decision).getDetail(),
                    failedContext,
                    MailRecordDisposition.EXCEPTION,
                    false,
                    null);
        }

        if (direction == MailDirection.OUTBOUND) {
            MailCryptoSelectionService.OutboundCryptoSelection outboundCrypto =
                    cryptoSelectionService.prepareOutbound(
                            envelope,
                            decision,
                            domainConfig,
                            processingDecision,
                            certificates,
                            cryptoProfile);
            processingDecision = outboundCrypto.decision();
            certificates = outboundCrypto.certificates();
            cryptoProfile = outboundCrypto.cryptoProfile();
        }

        boolean skipDecryption = direction == MailDirection.INBOUND && passthroughDecryption(domainConfig);
        if (direction == MailDirection.INBOUND) {
            MailCryptoSelectionService.InboundCryptoSelection inboundCrypto =
                    cryptoSelectionService.prepareInbound(certificates, envelope, skipDecryption);
            certificates = inboundCrypto.certificates();
            processingDecision = processingDecision
                    .withDecryptionRequired(inboundCrypto.decryptionRequired())
                    .withVerificationRequired(inboundCrypto.verificationRequired());
        }

        if (decision instanceof RoutingDecision.Quarantine quarantine) {
            processingDecision = processingDecision.withQuarantine(quarantine.getReason(), quarantine.getDetail());
            baseContext = baseContext.withRecordDisposition(MailRecordDisposition.EXCEPTION);
        }

        if (direction == MailDirection.OUTBOUND && dkimSigningEnabled(envelope.getSender().getDomain())) {
            processingDecision = processingDecision.withDkimSigningRequired(true);
        }

        MailProcessingContext context = routedContext(
                baseContext,
                decision,
                processingId,
                direction,
                cryptoProfile,
                processingDecision,
                certificates,
                relayProfile);
        routingAuditPublisher.publishRouting(context, decision);
        routingAuditPublisher.publishCertificateSelection(context);
        return MessageBuilder.withPayload(message.getPayload())
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .setHeader(MailProcessingHeaders.SKIP_DECRYPTION, skipDecryption)
                .build();
    }

    private boolean passthroughDecryption(DomainConfig domainConfig) {
        return domainConfig != null
                && domainConfig.getDecryptionMode() == DecryptionMode.END_TO_END_PASSTHROUGH;
    }

    private MailProcessingContext routedContext(MailProcessingContext baseContext,
                                                RoutingDecision decision,
                                                String processingId,
                                                MailDirection direction,
                                                CryptoProfile cryptoProfile,
                                                MailProcessingDecision processingDecision,
                                                CertificateSelection certificates,
                                                RelayProfile relayProfile) {
        MailProcessingContext context = baseContext
                .withRoutingDecision(decision)
                .withProcessingId(processingId)
                .withDirection(direction)
                .withCryptoProfile(cryptoProfile)
                .withDecision(processingDecision)
                .withCertificateSelection(certificates)
                .withRelayProfile(relayProfile);
        AuditTrace auditTrace = context.auditTrace();
        if (auditTrace != null) {
            context = context.withAuditTrace(new AuditTrace(
                    processingId,
                    auditTrace.correlationId(),
                    auditTrace.messageId(),
                    auditTrace.submissionType(),
                    auditTrace.remoteAddress()));
        }
        return context;
    }

    private CryptoProfile resolveCryptoProfile(MailProcessingContext context, DomainConfig domainConfig) {
        if (context.cryptoProfile() != null && context.cryptoProfile().isConcrete()) {
            return context.cryptoProfile();
        }
        return CryptoProfile.fromDomainConfig(domainConfig);
    }

    private boolean isOutboundCryptoProfileFailure(MailDirection direction, RoutingDecision decision) {
        return direction == MailDirection.OUTBOUND
                && decision instanceof RoutingDecision.Quarantine quarantine
                && quarantine.getReason() == QuarantineReason.CERTIFICATE_MISSING;
    }

    private boolean dkimSigningEnabled(String domain) {
        if (mailAuthPolicyRepository == null || domain == null || domain.isBlank()) {
            return false;
        }
        return mailAuthPolicyRepository.findDomainPolicy(domain)
                .map(policy -> policy.enabled() && policy.dkimSigningPolicy().signingReady())
                .orElse(false);
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    private String processingId(MailProcessingContext context) {
        return context != null && context.processingId() != null && !context.processingId().isBlank()
                ? context.processingId()
                : java.util.UUID.randomUUID().toString();
    }

}
