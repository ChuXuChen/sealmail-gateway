package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.exceptionmail.ExceptionMail;
import com.sealmail.domain.exceptionmail.ExceptionMailRepository;
import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.quarantine.spi.QuarantineNotificationPort;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarantineStepTest {

    @Test
    void dlpQuarantineRemainsPendingForAdminReview() {
        QuarantineRepository repository = mock(QuarantineRepository.class);
        ExceptionMailRepository exceptionMailRepository = mock(ExceptionMailRepository.class);
        QuarantineNotificationPort notificationPort = mock(QuarantineNotificationPort.class);
        QuarantineStep step = new QuarantineStep(
                repository,
                exceptionMailRepository,
                policy(false),
                notificationPort);

        step.execute(message("DLP QUARANTINE: rule matched", MailRecordDisposition.DLP_QUARANTINE));

        QuarantinedMail saved = savedMail(repository);
        assertEquals(QuarantineStatus.QUARANTINED, saved.getStatus());
        verify(notificationPort, never()).notifyCreated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dlpQuarantineNotificationIsDrivenByRuntimePolicy() {
        QuarantineRepository repository = mock(QuarantineRepository.class);
        ExceptionMailRepository exceptionMailRepository = mock(ExceptionMailRepository.class);
        QuarantineNotificationPort notificationPort = mock(QuarantineNotificationPort.class);
        QuarantineStep step = new QuarantineStep(
                repository,
                exceptionMailRepository,
                policy(true),
                notificationPort);

        step.execute(message("DLP QUARANTINE: rule matched", MailRecordDisposition.DLP_QUARANTINE));

        QuarantinedMail saved = savedMail(repository);
        verify(notificationPort).notifyCreated(saved);
    }

    @Test
    void exceptionMailIsRecordedSeparately() {
        QuarantineRepository repository = mock(QuarantineRepository.class);
        ExceptionMailRepository exceptionMailRepository = mock(ExceptionMailRepository.class);
        QuarantineNotificationPort notificationPort = mock(QuarantineNotificationPort.class);
        QuarantineStep step = new QuarantineStep(
                repository,
                exceptionMailRepository,
                policy(true),
                notificationPort);

        step.execute(message("DLP BLOCK: rule matched", MailRecordDisposition.EXCEPTION));

        ExceptionMail saved = savedExceptionMail(exceptionMailRepository);
        assertEquals(QuarantineReason.POLICY_VIOLATION, saved.getReason());
        assertEquals("system", saved.getBlockedBy());
        assertEquals("DLP policy blocked", saved.getBlockComment());
        assertTrue(saved.hasRawContent());
        verify(notificationPort, never()).notifyCreated(org.mockito.ArgumentMatchers.any());
    }

    private QuarantinePolicyPort policy(boolean notificationEnabled) {
        QuarantinePolicyPort port = mock(QuarantinePolicyPort.class);
        when(port.getSettings()).thenReturn(new QuarantinePolicyPort.QuarantinePolicySettings(
                30,
                notificationEnabled,
                false,
                Instant.now()));
        return port;
    }

    private org.springframework.messaging.Message<byte[]> message(String detail, MailRecordDisposition disposition) {
        byte[] payload = "Subject: dlp\r\n\r\nbody\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        MailEnvelope envelope = new MailEnvelope(
                "msg@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload
        );
        MailProcessingContext context = MailProcessingContext.create(envelope)
                .withDecision(com.sealmail.domain.mailsecurity.MailProcessingDecision.none()
                        .withQuarantine(QuarantineReason.POLICY_VIOLATION, detail))
                .withRecordDisposition(disposition);
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private QuarantinedMail savedMail(QuarantineRepository repository) {
        ArgumentCaptor<QuarantinedMail> captor = ArgumentCaptor.forClass(QuarantinedMail.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().hasRawContent());
        return captor.getValue();
    }

    private ExceptionMail savedExceptionMail(ExceptionMailRepository repository) {
        ArgumentCaptor<ExceptionMail> captor = ArgumentCaptor.forClass(ExceptionMail.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
