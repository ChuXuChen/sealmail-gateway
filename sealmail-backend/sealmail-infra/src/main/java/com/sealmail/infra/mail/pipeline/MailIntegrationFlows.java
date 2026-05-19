package com.sealmail.infra.mail.pipeline;

import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

@Component
public class MailIntegrationFlows {

    private final InboundMailFlowAssembler inboundMailFlowAssembler;
    private final OutboundMailFlowAssembler outboundMailFlowAssembler;
    private final QuarantineMailFlowAssembler quarantineMailFlowAssembler;
    private final RelayMailFlowAssembler relayMailFlowAssembler;
    private final QuarantineReleaseFlowAssembler quarantineReleaseFlowAssembler;

    public MailIntegrationFlows(InboundMailFlowAssembler inboundMailFlowAssembler,
                                OutboundMailFlowAssembler outboundMailFlowAssembler,
                                QuarantineMailFlowAssembler quarantineMailFlowAssembler,
                                RelayMailFlowAssembler relayMailFlowAssembler,
                                QuarantineReleaseFlowAssembler quarantineReleaseFlowAssembler) {
        this.inboundMailFlowAssembler = inboundMailFlowAssembler;
        this.outboundMailFlowAssembler = outboundMailFlowAssembler;
        this.quarantineMailFlowAssembler = quarantineMailFlowAssembler;
        this.relayMailFlowAssembler = relayMailFlowAssembler;
        this.quarantineReleaseFlowAssembler = quarantineReleaseFlowAssembler;
    }

    public IntegrationFlow inboundFlow(MessageChannel mailInboundChannel,
                                       MessageChannel quarantineChannel,
                                       MessageChannel relayChannel) {
        return inboundMailFlowAssembler.build(mailInboundChannel, quarantineChannel, relayChannel);
    }

    public IntegrationFlow outboundFlow(MessageChannel mailOutboundChannel,
                                        MessageChannel quarantineChannel,
                                        MessageChannel relayChannel) {
        return outboundMailFlowAssembler.build(mailOutboundChannel, quarantineChannel, relayChannel);
    }

    public IntegrationFlow quarantineFlow(MessageChannel quarantineChannel) {
        return quarantineMailFlowAssembler.build(quarantineChannel);
    }

    public IntegrationFlow relayFlow(MessageChannel relayChannel,
                                     MessageChannel quarantineChannel) {
        return relayMailFlowAssembler.build(relayChannel, quarantineChannel);
    }

    public IntegrationFlow quarantineReleaseFlow(MessageChannel quarantineReleaseChannel) {
        return quarantineReleaseFlowAssembler.build(quarantineReleaseChannel);
    }
}
