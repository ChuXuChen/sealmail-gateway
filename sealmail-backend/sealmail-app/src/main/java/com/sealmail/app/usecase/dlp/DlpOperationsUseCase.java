package com.sealmail.app.usecase.dlp;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.request.DlpFalsePositiveRequest;
import com.sealmail.app.dto.request.DlpTestApiRequest;
import com.sealmail.app.dto.response.DlpEvaluationResponse;
import com.sealmail.app.dto.response.DlpEventResponse;
import com.sealmail.app.dto.response.DlpEvidenceResponse;
import com.sealmail.app.dto.response.DlpUbaSenderRiskResponse;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.dlp.DlpEvaluationResult;
import com.sealmail.domain.dlp.DlpEvidence;
import com.sealmail.domain.dlp.DlpScanEvent;
import com.sealmail.domain.dlp.DlpTestRequest;
import com.sealmail.domain.dlp.DlpUbaSenderRisk;
import com.sealmail.domain.dlp.spi.DlpEvaluationPort;
import com.sealmail.domain.dlp.spi.DlpEventRepository;
import com.sealmail.domain.dlp.spi.DlpUbaAnalyticsPort;
import com.sealmail.domain.mailsecurity.MailDirection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DlpOperationsUseCase {

    private final DlpEvaluationPort dlpEvaluationPort;
    private final DlpEventRepository eventRepository;
    private final DlpUbaAnalyticsPort ubaAnalyticsPort;
    private final PermissionChecker permissionChecker;

    public DlpOperationsUseCase(DlpEvaluationPort dlpEvaluationPort,
                                DlpEventRepository eventRepository,
                                DlpUbaAnalyticsPort ubaAnalyticsPort,
                                PermissionChecker permissionChecker) {
        this.dlpEvaluationPort = dlpEvaluationPort;
        this.eventRepository = eventRepository;
        this.ubaAnalyticsPort = ubaAnalyticsPort;
        this.permissionChecker = permissionChecker;
    }

    @Transactional(readOnly = true)
    public DlpEvaluationResponse test(DlpTestApiRequest request, UserContext user) {
        permissionChecker.checkCanManageQuarantine(user);
        return toEvaluationResponse(dlpEvaluationPort.test(toTestRequest(request)));
    }

    @Transactional(readOnly = true)
    public DlpEvaluationResponse simulatePolicy(String policyId, DlpTestApiRequest request, UserContext user) {
        permissionChecker.checkCanManageQuarantine(user);
        return toEvaluationResponse(dlpEvaluationPort.simulatePolicy(policyId, toTestRequest(request)));
    }

    @Transactional(readOnly = true)
    public PageResponse<DlpEventResponse> listEvents(PageRequest pageRequest,
                                                     String action,
                                                     Integer minSeverity,
                                                     String rule,
                                                     String domain,
                                                     UserContext user) {
        permissionChecker.checkCanViewQuarantine(user);
        int offset = Math.max(0, (pageRequest.getPage() - 1) * pageRequest.getSize());
        List<DlpEventResponse> items = eventRepository.findEvents(
                        offset,
                        pageRequest.getSize(),
                        action,
                        minSeverity,
                        rule,
                        domain).stream()
                .map(this::toEventResponse)
                .toList();
        long total = eventRepository.countEvents(action, minSeverity, rule, domain);
        return PageResponse.of(items, total, pageRequest);
    }

    @Transactional(readOnly = true)
    public List<DlpEvidenceResponse> eventEvidence(String eventId, UserContext user) {
        permissionChecker.checkCanViewQuarantine(user);
        return eventRepository.findEvidenceByEventId(eventId).stream()
                .map(this::toEvidenceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DlpEvidenceResponse> quarantineEvidence(String quarantineId, UserContext user) {
        permissionChecker.checkCanViewQuarantine(user);
        return eventRepository.findEvidenceByQuarantineId(quarantineId).stream()
                .map(this::toEvidenceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DlpUbaSenderRiskResponse> listUbaSenderRisks(int limit, UserContext user) {
        permissionChecker.checkCanViewQuarantine(user);
        return ubaAnalyticsPort.listSenderRisks(limit).stream()
                .map(this::toUbaRiskResponse)
                .toList();
    }

    @Transactional
    public void markFalsePositive(String quarantineId, DlpFalsePositiveRequest request, UserContext user) {
        permissionChecker.checkCanManageQuarantine(user);
        eventRepository.markQuarantineFalsePositive(
                quarantineId,
                user.getUsername() != null ? user.getUsername() : user.getUserId(),
                request != null ? request.comment() : null);
    }

    private DlpTestRequest toTestRequest(DlpTestApiRequest request) {
        return new DlpTestRequest(
                request != null ? request.subject() : null,
                request != null ? request.body() : null,
                request != null ? request.sender() : null,
                request != null ? request.recipients() : null,
                parseDirection(request != null ? request.direction() : null)
        );
    }

    private MailDirection parseDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        return MailDirection.valueOf(direction.toUpperCase(java.util.Locale.ROOT));
    }

    private DlpEvaluationResponse toEvaluationResponse(DlpEvaluationResult result) {
        return new DlpEvaluationResponse(
                result.eventId(),
                result.action().name(),
                result.recommendedAction().name(),
                result.maxSeverity(),
                result.matches().size(),
                result.policyIds(),
                result.ruleGroupIds(),
                result.monitorMode(),
                result.scanDurationMs(),
                result.warnings(),
                result.evidence().stream().map(this::toEvidenceResponse).toList(),
                result.ubaRiskLevel().name(),
                result.ubaRiskReasons(),
                result.ubaActionUpgraded()
        );
    }

    private DlpEventResponse toEventResponse(DlpScanEvent event) {
        return new DlpEventResponse(
                event.id(),
                event.messageId(),
                event.processingId(),
                event.direction() != null ? event.direction().name() : null,
                event.senderEmail(),
                event.recipients(),
                event.subject(),
                event.remoteAddress(),
                event.policyIds(),
                event.ruleGroupIds(),
                event.action().name(),
                event.maxSeverity(),
                event.matchCount(),
                event.extractionWarnings(),
                event.monitorMode(),
                event.scanDurationMs(),
                event.ubaRiskLevel().name(),
                event.ubaRiskReasons(),
                event.ubaActionUpgraded(),
                event.quarantineId(),
                event.falsePositive(),
                event.falsePositiveAt(),
                event.falsePositiveBy(),
                event.falsePositiveComment(),
                event.createdAt()
        );
    }

    private DlpEvidenceResponse toEvidenceResponse(DlpEvidence evidence) {
        return new DlpEvidenceResponse(
                evidence.id(),
                evidence.eventId(),
                evidence.ruleId(),
                evidence.ruleName(),
                evidence.ruleType().name(),
                evidence.partId(),
                evidence.partKind().name(),
                evidence.fileName(),
                evidence.contentType(),
                evidence.maskedSnippet(),
                evidence.matchHash(),
                evidence.startOffset(),
                evidence.endOffset(),
                evidence.severity(),
                evidence.action().name(),
                evidence.createdAt()
        );
    }

    private DlpUbaSenderRiskResponse toUbaRiskResponse(DlpUbaSenderRisk risk) {
        return new DlpUbaSenderRiskResponse(
                risk.senderEmail(),
                risk.totalMessages(),
                risk.outboundMessages(),
                risk.externalDomainCount(),
                risk.dlpHitCount(),
                risk.highRiskCount(),
                risk.riskLevel().name(),
                risk.lastReasons(),
                risk.firstSeenAt(),
                risk.lastSeenAt(),
                risk.updatedAt()
        );
    }
}
