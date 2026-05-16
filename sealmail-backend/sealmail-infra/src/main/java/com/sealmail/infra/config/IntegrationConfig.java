package com.sealmail.infra.config;

import com.sealmail.infra.mail.pipeline.MailFlowErrorChannelInterceptor;
import com.sealmail.infra.mail.pipeline.MailIntegrationFlows;
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
    public MessageChannel mailInboundChannel(MailFlowErrorChannelInterceptor errorInterceptor) {
        return interceptedChannel(errorInterceptor);
    }

    @Bean
    public MessageChannel mailOutboundChannel(MailFlowErrorChannelInterceptor errorInterceptor) {
        return interceptedChannel(errorInterceptor);
    }

    @Bean
    public MessageChannel quarantineChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel quarantineReleaseChannel(MailFlowErrorChannelInterceptor errorInterceptor) {
        return interceptedChannel(errorInterceptor);
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
    public IntegrationFlow inboundProcessingFlow(MailIntegrationFlows mailIntegrationFlows,
                                                  MessageChannel mailInboundChannel,
                                                  MessageChannel quarantineChannel,
                                                  MessageChannel relayChannel) {
        return mailIntegrationFlows.inboundFlow(mailInboundChannel, quarantineChannel, relayChannel);
    }

    @Bean
    public IntegrationFlow outboundProcessingFlow(MailIntegrationFlows mailIntegrationFlows,
                                                   MessageChannel mailOutboundChannel,
                                                   MessageChannel quarantineChannel,
                                                   MessageChannel relayChannel) {
        return mailIntegrationFlows.outboundFlow(mailOutboundChannel, quarantineChannel, relayChannel);
    }

    @Bean
    public IntegrationFlow quarantineProcessingFlow(MailIntegrationFlows mailIntegrationFlows,
                                                     MessageChannel quarantineChannel) {
        return mailIntegrationFlows.quarantineFlow(quarantineChannel);
    }

    @Bean
    public IntegrationFlow relayProcessingFlow(MailIntegrationFlows mailIntegrationFlows,
                                                MessageChannel relayChannel,
                                                MessageChannel quarantineChannel) {
        return mailIntegrationFlows.relayFlow(relayChannel, quarantineChannel);
    }

    @Bean
    public IntegrationFlow quarantineReleaseProcessingFlow(MailIntegrationFlows mailIntegrationFlows,
                                                           MessageChannel quarantineReleaseChannel) {
        return mailIntegrationFlows.quarantineReleaseFlow(quarantineReleaseChannel);
    }

    private MessageChannel interceptedChannel(MailFlowErrorChannelInterceptor errorInterceptor) {
        DirectChannel channel = new DirectChannel();
        channel.addInterceptor(errorInterceptor);
        return channel;
    }
}
