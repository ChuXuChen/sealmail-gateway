package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.ProcessingStep;
import org.springframework.messaging.Message;

/**
 * Pipeline step interface for mail processing.
 * Each step handles a specific aspect of mail security.
 */
public interface MailPipelineStep {

    /**
     * Execute the pipeline step.
     * @param message The mail message with headers containing envelope and certificates
     * @return Processed message payload
     */
    PipelineResult execute(Message<byte[]> message);

    /**
     * @return Name of this pipeline step
     */
    String getStepName();

    /**
     * @return Whether this step is enabled
     */
    default boolean isEnabled() {
        return true;
    }
}
