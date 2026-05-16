package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.exceptionmail.ExceptionMail;
import com.sealmail.domain.exceptionmail.ExceptionMailRepository;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.persistence.repository.QuarantineRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QuarantineStepTest {

    @Test
    void dlpQuarantineRemainsPendingForAdminReview() {
        QuarantineRepositoryImpl repository = mock(QuarantineRepositoryImpl.class);
        ExceptionMailRepository exceptionMailRepository = mock(ExceptionMailRepository.class);
        QuarantineStep step = new QuarantineStep(repository, exceptionMailRepository);

        step.execute(message("DLP QUARANTINE: rule matched", MailRecordDisposition.DLP_QUARANTINE));

        QuarantinedMail saved = savedMail(repository);
        assertEquals(QuarantineStatus.QUARANTINED, saved.getStatus());
    }

    @Test
    void exceptionMailIsRecordedSeparately() {
        QuarantineRepositoryImpl repository = mock(QuarantineRepositoryImpl.class);
        ExceptionMailRepository exceptionMailRepository = mock(ExceptionMailRepository.class);
        QuarantineStep step = new QuarantineStep(repository, exceptionMailRepository);

        step.execute(message("DLP BLOCK: rule matched", MailRecordDisposition.EXCEPTION));

        ExceptionMail saved = savedExceptionMail(exceptionMailRepository);
        assertEquals(QuarantineReason.POLICY_VIOLATION, saved.getReason());
        assertEquals("system", saved.getBlockedBy());
        assertEquals("DLP policy blocked", saved.getBlockComment());
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

    private QuarantinedMail savedMail(QuarantineRepositoryImpl repository) {
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
