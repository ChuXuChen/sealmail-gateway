package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.infra.mail.auth.DkimSigner;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class DkimSignStep implements MailPipelineStep {

    private final DkimSigner dkimSigner;

    public DkimSignStep(DkimSigner dkimSigner) {
        this.dkimSigner = dkimSigner;
    }

    @Override
    public PipelineResult execute(Message<byte[]> message) {
        Boolean dkimEnabled = (Boolean) message.getHeaders().get("dkimEnabled");
        if (dkimEnabled == null || !dkimEnabled) {
            return PipelineResult.success(message.getPayload());
        }
        MailEnvelope envelope = (MailEnvelope) message.getHeaders().get("mailEnvelope");
        if (envelope == null) {
            return PipelineResult.success(message.getPayload());
        }
        return PipelineResult.success(dkimSigner.sign(message.getPayload(), envelope.getSender().getDomain()));
    }

    @Override
    public String getStepName() {
        return "dkim-sign";
    }
}
