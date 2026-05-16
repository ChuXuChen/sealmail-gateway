package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterHandler.class);

    private final MessageChannel quarantineChannel;
    private final MailErrorDecisionHandler errorDecisionHandler;
    private final ConcurrentLinkedDeque<DeadLetterEntry> recentErrors = new ConcurrentLinkedDeque<>();
    private final AtomicLong totalDeadLetters = new AtomicLong(0);
    private final int maxRecentErrors = 100;

    public DeadLetterHandler(@Qualifier("quarantineChannel") MessageChannel quarantineChannel,
                             MailErrorDecisionHandler errorDecisionHandler) {
        this.quarantineChannel = quarantineChannel;
        this.errorDecisionHandler = errorDecisionHandler;
    }

    @ServiceActivator(inputChannel = "errorChannel")
    public void handleError(Message<?> message) {
        totalDeadLetters.incrementAndGet();

        Throwable error = (Throwable) message.getPayload();
        String errorMessage = error != null ? error.getMessage() : "Unknown error";
        String stackTrace = getStackTrace(error);

        log.error("Dead letter received: {}", errorMessage);
        if (error != null) {
            log.debug("Dead letter stack trace", error);
        }

        DeadLetterEntry entry = new DeadLetterEntry(
                Instant.now(),
                errorMessage,
                stackTrace,
                extractHeaders(message)
        );
        recentErrors.addFirst(entry);

        while (recentErrors.size() > maxRecentErrors) {
            recentErrors.removeLast();
        }

        MailErrorDecisionHandler.ErrorDecision decision = errorDecisionHandler.decide(message);
        if (decision.quarantine()) {
            routeToQuarantine(decision.quarantineMessage());
        }
    }

    private void routeToQuarantine(Message<byte[]> message) {
        try {
            quarantineChannel.send(message);

            log.debug("Failed message routed to quarantine");
        } catch (Exception e) {
            log.error("Failed to route message to quarantine: {}", e.getMessage());
        }
    }

    private String getStackTrace(Throwable t) {
        if (t == null) {
            return "";
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        t.printStackTrace(ps);
        return baos.toString(StandardCharsets.UTF_8);
    }

    private Map<String, Object> extractHeaders(Message<?> message) {
        MailProcessingContext context = context(message);
        if (context == null
                && message instanceof ErrorMessage errorMessage
                && errorMessage.getOriginalMessage() != null) {
            context = context(errorMessage.getOriginalMessage());
        }
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("messageId", context != null ? context.envelope().getMessageId() : message.getHeaders().getId());
        headers.put("timestamp", message.getHeaders().getTimestamp());
        if (context != null) {
            headers.put("processingId", context.processingId());
            if (context.auditTrace() != null) {
                headers.put("correlationId", context.auditTrace().correlationId());
            }
        }
        return headers;
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    public List<DeadLetterEntry> getRecentErrors() {
        return new ArrayList<>(recentErrors);
    }

    public long getTotalDeadLetters() {
        return totalDeadLetters.get();
    }

    public void clearRecentErrors() {
        recentErrors.clear();
    }

    public static class DeadLetterEntry {
        private final Instant timestamp;
        private final String errorMessage;
        private final String stackTrace;
        private final Map<String, Object> headers;

        public DeadLetterEntry(Instant timestamp, String errorMessage,
                                String stackTrace, Map<String, Object> headers) {
            this.timestamp = timestamp;
            this.errorMessage = errorMessage;
            this.stackTrace = stackTrace;
            this.headers = headers;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }
    }
}
