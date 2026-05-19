package com.sealmail.infra.mail.pipeline;

import com.sealmail.infra.mail.pipeline.step.QuarantineStep;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

@Component
public class QuarantineMailFlowAssembler {

    private final MailFlowStepRunner stepRunner;
    private final QuarantineStep quarantineStep;

    public QuarantineMailFlowAssembler(MailFlowStepRunner stepRunner,
                                       QuarantineStep quarantineStep) {
        this.stepRunner = stepRunner;
        this.quarantineStep = quarantineStep;
    }

    public IntegrationFlow build(MessageChannel quarantineChannel) {
        return IntegrationFlow.from(quarantineChannel)
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.QUARANTINE, quarantineStep::execute))
                .nullChannel();
    }
}
