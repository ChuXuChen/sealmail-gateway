package com.sealmail.infra.mail.pipeline;

import com.sealmail.infra.mail.pipeline.step.RelayStep;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

@Component
public class RelayMailFlowAssembler {

    private final MailFlowStepRunner stepRunner;
    private final MailFlowRouteDecider routeDecider;
    private final MailFlowCompletionService completionService;
    private final RelayStep relayStep;

    public RelayMailFlowAssembler(MailFlowStepRunner stepRunner,
                                  MailFlowRouteDecider routeDecider,
                                  MailFlowCompletionService completionService,
                                  RelayStep relayStep) {
        this.stepRunner = stepRunner;
        this.routeDecider = routeDecider;
        this.completionService = completionService;
        this.relayStep = relayStep;
    }

    public IntegrationFlow build(MessageChannel relayChannel,
                                 MessageChannel quarantineChannel) {
        return IntegrationFlow.from(relayChannel)
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.RELAY, relayStep::execute))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .subFlowMapping(MailFlowRoute.RELAY, sf -> sf
                                        .handle(Message.class, (message, headers) ->
                                                completionService.completeSuccess(message))
                                        .nullChannel())
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputChannel("nullChannel"))
                .get();
    }
}
