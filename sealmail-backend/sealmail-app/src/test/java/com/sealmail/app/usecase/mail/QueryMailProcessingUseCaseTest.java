package com.sealmail.app.usecase.mail;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.mapper.AuditDtoMapper;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.audit.AuditLog;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QueryMailProcessingUseCaseTest {

    private final MailProcessingRepository repository = mock(MailProcessingRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final QueryMailProcessingUseCase useCase = new QueryMailProcessingUseCase(
            repository,
            auditLogRepository,
            new AuditDtoMapper());

    @Test
    void detailReturnsStatusSnapshot() {
        MailProcessing processing = processing();
        MailProcessingStatusSnapshot snapshot = MailProcessingStatusSnapshot.empty()
                .withMailAuth(new MailProcessingStatusSnapshot.MailAuthStatus(
                        MailProcessingStatusSnapshot.PASS,
                        null,
                        null,
                        null,
                        "LOG_ONLY",
                        null,
                        "DMARC pass"));
        processing.updateStatusSnapshot(snapshot);
        when(repository.findById("processing-1")).thenReturn(Optional.of(processing));

        var response = useCase.findById("processing-1", auditor());

        assertEquals("processing-1", response.getProcessingId());
        assertSame(snapshot, response.getStatusSnapshot());
        assertEquals(MailProcessingStatusSnapshot.PASS, response.getStatusSnapshot().mailAuth().status());
    }

    @Test
    void listReturnsStatusSnapshot() {
        MailProcessing processing = processing();
        when(repository.findRecent(1, 10)).thenReturn(List.of(processing));
        when(repository.count()).thenReturn(1L);

        var response = useCase.listRecent(PageRequest.builder().page(1).size(10).build(), auditor());

        assertEquals(1, response.getTotal());
        assertEquals(MailProcessingStatusSnapshot.PENDING,
                response.getItems().getFirst().getStatusSnapshot().mailAuth().status());
    }

    @Test
    void dispositionPrefersUnifiedFinalDisposition() {
        MailProcessing processing = processing();
        processing.completeProcessing(ProcessingResult.FAILED);
        processing.updateStatusSnapshot(MailProcessingStatusSnapshot.empty()
                .withFinalDisposition(new MailProcessingStatusSnapshot.FinalDispositionStatus(
                        MailProcessingStatusSnapshot.QUARANTINED,
                        ProcessingResult.FAILED.name(),
                        "QUARANTINE",
                        "POLICY_VIOLATION",
                        "DLP rule matched"))
                .withFailure(new MailProcessingStatusSnapshot.FailureStatus(
                        MailProcessingStatusSnapshot.FAIL,
                        "dlp",
                        "DLP",
                        "QUARANTINE",
                        "DLP rule matched",
                        false)));
        when(repository.findById("processing-1")).thenReturn(Optional.of(processing));

        var response = useCase.findById("processing-1", auditor());

        assertEquals("QUARANTINED", response.getDisposition());
        assertEquals("dlp", response.getFailedStep());
        assertEquals("DLP rule matched", response.getFailureReason());
    }

    @Test
    void auditTraceUsesMailProcessingResource() {
        MailProcessing processing = processing();
        AuditLog log = AuditLog.builder()
                .id("audit-1")
                .type(AuditLogType.EMAIL_RELAYED)
                .resourceType("MAIL_PROCESSING")
                .resourceId("processing-1")
                .action("SMTP_RELAY")
                .success(true)
                .occurredAt(Instant.parse("2026-05-20T10:00:00Z"))
                .build();
        when(repository.findById("processing-1")).thenReturn(Optional.of(processing));
        when(auditLogRepository.findByResource("MAIL_PROCESSING", "processing-1", 1, 20))
                .thenReturn(List.of(log));
        when(auditLogRepository.countByResource("MAIL_PROCESSING", "processing-1")).thenReturn(1L);

        var response = useCase.auditTrace(
                "processing-1",
                PageRequest.builder().page(1).size(20).build(),
                auditor());

        assertEquals(1, response.getTotal());
        assertEquals("SMTP_RELAY", response.getItems().getFirst().getAction());
        verify(auditLogRepository).findByResource("MAIL_PROCESSING", "processing-1", 1, 20);
        verify(auditLogRepository).countByResource("MAIL_PROCESSING", "processing-1");
    }

    @Test
    void blankProcessingIdReturnsBusinessError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> useCase.findById(" ", auditor()));

        assertEquals("BAD_REQUEST", exception.getCode());
    }

    @Test
    void auditTraceRequiresPermission() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> useCase.auditTrace(
                        "processing-1",
                        PageRequest.builder().page(1).size(20).build(),
                        UserContext.builder().roles(Set.of("USER")).build()));

        assertEquals("FORBIDDEN", exception.getCode());
        verifyNoInteractions(repository, auditLogRepository);
    }

    private static MailProcessing processing() {
        MailProcessing processing = MailProcessing.create("processing-1", envelope(), MailDirection.INBOUND);
        processing.completeProcessing(ProcessingResult.SUCCESS);
        processing.clearDomainEvents();
        return processing;
    }

    private static MailEnvelope envelope() {
        return new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.parse("2026-05-20T09:00:00Z"),
                "body".getBytes());
    }

    private static UserContext auditor() {
        return UserContext.builder()
                .userId("auditor-1")
                .username("auditor")
                .roles(Set.of("AUDITOR"))
                .build();
    }
}
