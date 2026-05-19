package com.sealmail.infra.mail.pipeline;

import com.sealmail.infra.mail.pipeline.step.DecryptStep;
import com.sealmail.infra.mail.pipeline.step.DlpStep;
import com.sealmail.infra.mail.pipeline.step.MailAuthenticationStep;
import com.sealmail.infra.mail.pipeline.step.VerifyStep;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

@Component
public class InboundMailFlowAssembler {

    private final MailRoutingStep routingStep;
    private final MailFlowStepRunner stepRunner;
    private final MailFlowRouteDecider routeDecider;
    private final MailAuthenticationStep mailAuthenticationStep;
    private final DecryptStep decryptStep;
    private final VerifyStep verifyStep;
    private final DlpStep dlpStep;

    public InboundMailFlowAssembler(MailRoutingStep routingStep,
                                    MailFlowStepRunner stepRunner,
                                    MailFlowRouteDecider routeDecider,
                                    MailAuthenticationStep mailAuthenticationStep,
                                    DecryptStep decryptStep,
                                    VerifyStep verifyStep,
                                    DlpStep dlpStep) {
        this.routingStep = routingStep;
        this.stepRunner = stepRunner;
        this.routeDecider = routeDecider;
        this.mailAuthenticationStep = mailAuthenticationStep;
        this.decryptStep = decryptStep;
        this.verifyStep = verifyStep;
        this.dlpStep = dlpStep;
    }

    public IntegrationFlow build(MessageChannel mailInboundChannel,
                                 MessageChannel quarantineChannel,
                                 MessageChannel relayChannel) {
        return IntegrationFlow.from(mailInboundChannel)
                .handle(Message.class, (message, headers) ->
                        stepRunner.runFlowAction(message, MailFlowStep.ROUTING, routingStep::routeInbound))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.MAIL_AUTH,
                                mailAuthenticationStep::execute))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.DECRYPT, decryptStep::execute))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.VERIFY_SIGNATURE,
                                verifyStep::execute))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.DLP, dlpStep::execute))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.RELAY, relayChannel)
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputChannel("nullChannel"))
                .get();
    }
}
