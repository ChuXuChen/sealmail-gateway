package com.sealmail.infra.mail.pipeline;

import com.sealmail.infra.mail.pipeline.step.DkimSignStep;
import com.sealmail.infra.mail.pipeline.step.EncryptStep;
import com.sealmail.infra.mail.pipeline.step.RelayStep;
import com.sealmail.infra.mail.pipeline.step.SignStep;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

@Component
public class QuarantineReleaseFlowAssembler {

    private final MailRoutingStep routingStep;
    private final MailFlowStepRunner stepRunner;
    private final MailFlowRouteDecider routeDecider;
    private final QuarantineReleaseGuard releaseGuard;
    private final MailFlowCompletionService completionService;
    private final SignStep signStep;
    private final EncryptStep encryptStep;
    private final DkimSignStep dkimSignStep;
    private final RelayStep relayStep;

    public QuarantineReleaseFlowAssembler(MailRoutingStep routingStep,
                                          MailFlowStepRunner stepRunner,
                                          MailFlowRouteDecider routeDecider,
                                          QuarantineReleaseGuard releaseGuard,
                                          MailFlowCompletionService completionService,
                                          SignStep signStep,
                                          EncryptStep encryptStep,
                                          DkimSignStep dkimSignStep,
                                          RelayStep relayStep) {
        this.routingStep = routingStep;
        this.stepRunner = stepRunner;
        this.routeDecider = routeDecider;
        this.releaseGuard = releaseGuard;
        this.completionService = completionService;
        this.signStep = signStep;
        this.encryptStep = encryptStep;
        this.dkimSignStep = dkimSignStep;
        this.relayStep = relayStep;
    }

    public IntegrationFlow build(MessageChannel quarantineReleaseChannel) {
        return IntegrationFlow.from(quarantineReleaseChannel)
                .handle(Message.class, (message, headers) ->
                        runGuardedReleaseFlowAction(message, MailFlowStep.ROUTING, routingStep::routeReleasedMail))
                .route(Message.class, routeDecider::releaseDirectionRoute,
                        mapping -> mapping
                                .subFlowMapping(MailFlowRoute.RELEASE_OUTBOUND, outboundReleaseFlow())
                                .subFlowMapping(MailFlowRoute.RELEASE_INBOUND, inboundReleaseFlow())
                                .defaultOutputChannel("nullChannel"))
                .get();
    }

    private IntegrationFlow outboundReleaseFlow() {
        return flow -> flow
                .handle(Message.class, (message, headers) ->
                        runGuardedReleaseStep(message, MailFlowStep.SIGN, signStep::execute))
                .handle(Message.class, (message, headers) ->
                        runGuardedReleaseStep(message, MailFlowStep.ENCRYPT, encryptStep::execute))
                .handle(Message.class, (message, headers) ->
                        runGuardedReleaseStep(message, MailFlowStep.DKIM_SIGN, dkimSignStep::execute))
                .handle(Message.class, (message, headers) ->
                        runGuardedReleaseStep(message, MailFlowStep.RELAY, relayStep::execute))
                .handle(Message.class, (message, headers) -> completionService.completeSuccess(message))
                .nullChannel();
    }

    private IntegrationFlow inboundReleaseFlow() {
        return flow -> flow
                .route(Message.class, routeDecider::inboundReleaseRoute,
                        inboundMapping -> inboundMapping
                                .subFlowMapping(MailFlowRoute.ENCRYPT_THEN_RELAY, inboundEncryptThenRelayFlow())
                                .subFlowMapping(MailFlowRoute.RELAY, inboundRelayFlow())
                                .defaultOutputChannel("nullChannel"));
    }

    private IntegrationFlow inboundEncryptThenRelayFlow() {
        return flow -> flow
                .handle(Message.class, (message, headers) ->
                        runGuardedReleaseStep(message, MailFlowStep.ENCRYPT, encryptStep::execute))
                .handle(Message.class, (message, headers) ->
                        runGuardedReleaseStep(message, MailFlowStep.RELAY, relayStep::execute))
                .handle(Message.class, (message, headers) -> completionService.completeSuccess(message))
                .nullChannel();
    }

    private IntegrationFlow inboundRelayFlow() {
        return flow -> flow
                .handle(Message.class, (message, headers) ->
                        runGuardedReleaseStep(message, MailFlowStep.RELAY, relayStep::execute))
                .handle(Message.class, (message, headers) -> completionService.completeSuccess(message))
                .nullChannel();
    }

    private Message<byte[]> runGuardedReleaseFlowAction(Object message,
                                                        MailFlowStep step,
                                                        MailProcessingTracker.StepAction action) {
        Message<byte[]> result = stepRunner.runFlowAction(message, step, action);
        return releaseGuard.failIfQuarantined(result, step.errorType());
    }

    private Message<byte[]> runGuardedReleaseStep(Object message,
                                                  MailFlowStep step,
                                                  MailProcessingTracker.StepAction action) {
        Message<byte[]> result = runReleaseStep(message, step, action);
        return releaseGuard.failIfQuarantined(result, step.errorType());
    }

    private Message<byte[]> runReleaseStep(Object message,
                                           MailFlowStep step,
                                           MailProcessingTracker.StepAction action) {
        return stepRunner.runTrackedStep(message, step, action);
    }
}
