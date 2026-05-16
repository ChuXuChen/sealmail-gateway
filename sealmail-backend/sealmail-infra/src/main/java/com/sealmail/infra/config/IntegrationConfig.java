package com.sealmail.infra.config;

import com.sealmail.infra.mail.pipeline.MailPipelineFlow;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;

@Configuration
@EnableIntegration
public class IntegrationConfig {

    @Bean
    public MessageChannel mailInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel mailOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel quarantineChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel relayChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel errorChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow inboundProcessingFlow(MailPipelineFlow pipelineFlow,
                                                  MessageChannel mailInboundChannel,
                                                  MessageChannel quarantineChannel,
                                                  MessageChannel relayChannel) {
        return pipelineFlow.inboundFlow(mailInboundChannel, quarantineChannel, relayChannel);
    }

    @Bean
    public IntegrationFlow outboundProcessingFlow(MailPipelineFlow pipelineFlow,
                                                   MessageChannel mailOutboundChannel,
                                                   MessageChannel quarantineChannel,
                                                   MessageChannel relayChannel) {
        return pipelineFlow.outboundFlow(mailOutboundChannel, quarantineChannel, relayChannel);
    }

    @Bean
    public IntegrationFlow quarantineProcessingFlow(MailPipelineFlow pipelineFlow,
                                                     MessageChannel quarantineChannel) {
        return pipelineFlow.quarantineFlow(quarantineChannel);
    }

    @Bean
    public IntegrationFlow relayProcessingFlow(MailPipelineFlow pipelineFlow,
                                                MessageChannel relayChannel,
                                                MessageChannel quarantineChannel) {
        return pipelineFlow.relayFlow(relayChannel, quarantineChannel);
    }
}
