package com.sealmail.infra.mail.pipeline;

import com.sealmail.infra.mail.pipeline.step.DkimSignStep;
import com.sealmail.infra.mail.pipeline.step.AttachmentSecurityStep;
import com.sealmail.infra.mail.pipeline.step.DlpStep;
import com.sealmail.infra.mail.pipeline.step.EncryptStep;
import com.sealmail.infra.mail.pipeline.step.SignStep;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

@Component
public class OutboundMailFlowAssembler {

    private final MailRoutingStep routingStep;
    private final MailFlowStepRunner stepRunner;
    private final MailFlowRouteDecider routeDecider;
    private final AttachmentSecurityStep attachmentSecurityStep;
    private final DlpStep dlpStep;
    private final SignStep signStep;
    private final EncryptStep encryptStep;
    private final DkimSignStep dkimSignStep;

    public OutboundMailFlowAssembler(MailRoutingStep routingStep,
                                     MailFlowStepRunner stepRunner,
                                     MailFlowRouteDecider routeDecider,
                                     AttachmentSecurityStep attachmentSecurityStep,
                                     DlpStep dlpStep,
                                     SignStep signStep,
                                     EncryptStep encryptStep,
                                     DkimSignStep dkimSignStep) {
        this.routingStep = routingStep;
        this.stepRunner = stepRunner;
        this.routeDecider = routeDecider;
        this.attachmentSecurityStep = attachmentSecurityStep;
        this.dlpStep = dlpStep;
        this.signStep = signStep;
        this.encryptStep = encryptStep;
        this.dkimSignStep = dkimSignStep;
    }

    public IntegrationFlow build(MessageChannel mailOutboundChannel,
                                 MessageChannel quarantineChannel,
                                 MessageChannel relayChannel) {
        return IntegrationFlow.from(mailOutboundChannel)
                .handle(Message.class, (message, headers) ->
                        stepRunner.runFlowAction(message, MailFlowStep.ROUTING, routingStep::routeOutbound))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.ATTACHMENT_SECURITY,
                                attachmentSecurityStep::execute))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.DLP, dlpStep::execute))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.SIGN, signStep::execute))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.ENCRYPT, encryptStep::execute))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        stepRunner.runTrackedStep(message, MailFlowStep.DKIM_SIGN, dkimSignStep::execute))
                .route(Message.class, routeDecider::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(MailFlowRoute.RELAY, relayChannel)
                                .channelMapping(MailFlowRoute.QUARANTINE, quarantineChannel)
                                .defaultOutputChannel("nullChannel"))
                .get();
    }
}
