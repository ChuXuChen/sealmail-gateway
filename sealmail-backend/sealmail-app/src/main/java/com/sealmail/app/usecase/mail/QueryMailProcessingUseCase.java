package com.sealmail.app.usecase.mail;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.response.AuditLogResponse;
import com.sealmail.app.dto.response.MailProcessingResponse;
import com.sealmail.app.dto.response.MailProcessingStepResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.mapper.AuditDtoMapper;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.audit.AuditLog;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.mailsecurity.ProcessingStep;
import com.sealmail.domain.mailsecurity.RoutingDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QueryMailProcessingUseCase {

    private static final String MAIL_PROCESSING_RESOURCE = "MAIL_PROCESSING";

    private final MailProcessingRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final AuditDtoMapper auditDtoMapper;

    @Transactional(readOnly = true)
    public PageResponse<MailProcessingResponse> listRecent(PageRequest pageRequest, UserContext currentUser) {
        assertCanView(currentUser);
        List<MailProcessingResponse> items = repository
                .findRecent(pageRequest.getPage(), pageRequest.getSize())
                .stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.of(items, repository.count(), pageRequest);
    }

    @Transactional(readOnly = true)
    public MailProcessingResponse findById(String processingId, UserContext currentUser) {
        assertCanView(currentUser);
        if (processingId == null || processingId.isBlank()) {
            throw BusinessException.badRequest("邮件处理 ID 不能为空");
        }
        return repository.findById(processingId)
                .map(this::toResponse)
                .orElseThrow(() -> BusinessException.badRequest("邮件处理记录不存在: " + processingId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> auditTrace(String processingId,
                                                     PageRequest pageRequest,
                                                     UserContext currentUser) {
        assertCanView(currentUser);
        if (processingId == null || processingId.isBlank()) {
            throw BusinessException.badRequest("邮件处理 ID 不能为空");
        }
        repository.findById(processingId)
                .orElseThrow(() -> BusinessException.badRequest("邮件处理记录不存在: " + processingId));
        List<AuditLog> logs = auditLogRepository.findByResource(
                MAIL_PROCESSING_RESOURCE,
                processingId,
                pageRequest.getPage(),
                pageRequest.getSize());
        long total = auditLogRepository.countByResource(MAIL_PROCESSING_RESOURCE, processingId);
        return PageResponse.of(logs.stream().map(auditDtoMapper::toResponse).toList(), total, pageRequest);
    }

    private void assertCanView(UserContext currentUser) {
        if (currentUser == null || (!currentUser.canViewQuarantine() && !currentUser.canOperateMailTools())) {
            throw BusinessException.forbidden("没有权限查看邮件处理状态");
        }
    }

    private MailProcessingResponse toResponse(MailProcessing processing) {
        List<MailProcessingStepResponse> steps = processing.getSteps().stream()
                .sorted(Comparator.comparing(ProcessingStep::getStartedAt))
                .map(this::toStepResponse)
                .toList();
        ProcessingStep failedStep = processing.getSteps().stream()
                .filter(step -> step.isCompleted() && !step.isSuccess())
                .reduce((first, second) -> second)
                .orElse(null);
        MailProcessingStatusSnapshot.FailureStatus snapshotFailure = processing.getStatusSnapshot().failure();
        boolean hasSnapshotFailure = snapshotFailure != null
                && MailProcessingStatusSnapshot.FAIL.equals(snapshotFailure.status());
        return MailProcessingResponse.builder()
                .id(processing.getId())
                .processingId(processing.getId())
                .messageId(processing.getEnvelope().getMessageId())
                .direction(processing.getDirection().name())
                .sender(processing.getEnvelope().getSender().getValue())
                .recipients(processing.getEnvelope().getRecipients().stream()
                        .map(com.sealmail.domain.shared.model.EmailAddress::getValue)
                        .toList())
                .remoteHost(processing.getEnvelope().getRemoteHost())
                .helo(processing.getEnvelope().getHelo())
                .receivedAt(processing.getEnvelope().getReceivedAt())
                .routingDecision(routingDecision(processing.getRoutingDecision()))
                .result(processing.getResult() != null ? processing.getResult().name() : null)
                .disposition(disposition(processing))
                .failedStep(hasSnapshotFailure ? snapshotFailure.step()
                        : failedStep != null ? failedStep.getStepName() : null)
                .failureReason(hasSnapshotFailure ? failureReason(snapshotFailure)
                        : failedStep != null ? failedStep.getErrorMessage() : null)
                .statusSnapshot(processing.getStatusSnapshot())
                .steps(steps)
                .build();
    }

    private MailProcessingStepResponse toStepResponse(ProcessingStep step) {
        return MailProcessingStepResponse.builder()
                .id(step.getId())
                .stepName(step.getStepName())
                .completed(step.isCompleted())
                .success(step.isSuccess())
                .errorMessage(step.getErrorMessage())
                .startedAt(step.getStartedAt())
                .completedAt(step.getCompletedAt())
                .build();
    }

    private String routingDecision(RoutingDecision decision) {
        if (decision == null) {
            return "UNKNOWN";
        }
        if (decision instanceof RoutingDecision.OutboundEncrypt) {
            return "OUTBOUND_ENCRYPT";
        }
        if (decision instanceof RoutingDecision.OutboundSign) {
            return "OUTBOUND_SIGN";
        }
        if (decision instanceof RoutingDecision.InboundDecrypt) {
            return "INBOUND_DECRYPT";
        }
        if (decision instanceof RoutingDecision.InboundVerify) {
            return "INBOUND_VERIFY";
        }
        if (decision instanceof RoutingDecision.PassThrough) {
            return "PASS_THROUGH";
        }
        if (decision instanceof RoutingDecision.Quarantine quarantine) {
            return "QUARANTINE:" + quarantine.getReason().name();
        }
        return decision.getClass().getSimpleName();
    }

    private String disposition(MailProcessing processing) {
        String snapshotDisposition = snapshotDisposition(processing.getStatusSnapshot());
        if (snapshotDisposition != null) {
            return snapshotDisposition;
        }
        if (processing.getResult() == null) {
            return "PROCESSING";
        }
        if (processing.getResult() == ProcessingResult.SUCCESS) {
            return "DELIVERED";
        }
        if (processing.getRoutingDecision() instanceof RoutingDecision.Quarantine) {
            return "QUARANTINED";
        }
        if (processing.getResult() == ProcessingResult.EXCEPTION) {
            return "EXCEPTION";
        }
        return "FAILED";
    }

    private String snapshotDisposition(MailProcessingStatusSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        String finalStatus = snapshot.finalDisposition() != null ? snapshot.finalDisposition().status() : null;
        String deliveryStatus = snapshot.delivery() != null ? snapshot.delivery().status() : null;
        String status = dispositionStatus(finalStatus) != null ? finalStatus : deliveryStatus;
        return dispositionStatus(status);
    }

    private String dispositionStatus(String status) {
        if (MailProcessingStatusSnapshot.DELIVERED.equals(status)) {
            return "DELIVERED";
        }
        if (MailProcessingStatusSnapshot.QUARANTINED.equals(status)) {
            return "QUARANTINED";
        }
        if (MailProcessingStatusSnapshot.EXCEPTION.equals(status)) {
            return "EXCEPTION";
        }
        if (MailProcessingStatusSnapshot.FAIL.equals(status)) {
            return "FAILED";
        }
        return null;
    }

    private String failureReason(MailProcessingStatusSnapshot.FailureStatus failure) {
        if (failure == null) {
            return null;
        }
        if (failure.detail() != null && !failure.detail().isBlank()) {
            return failure.detail();
        }
        return failure.reason();
    }
}
