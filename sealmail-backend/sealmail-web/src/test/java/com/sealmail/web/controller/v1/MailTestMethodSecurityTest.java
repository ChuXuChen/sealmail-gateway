package com.sealmail.web.controller.v1;

import com.sealmail.app.security.AppPermissionAuthorizer;
import com.sealmail.app.security.AppPermissionEvaluator;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.mail.MailTestUseCase;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.config.RelayPolicyPort;
import com.sealmail.domain.mail.spi.MailMessageComposer;
import com.sealmail.domain.mail.spi.OutboundMailSubmitter;
import com.sealmail.domain.mail.spi.SmtpRelayProbe;
import com.sealmail.domain.mailsecurity.CryptoProfileSelector;
import com.sealmail.domain.mailsecurity.DeliveryRouteResolver;
import com.sealmail.domain.policy.DeliveryTransportProfile;
import com.sealmail.domain.system.SystemSettingsProvider;
import com.sealmail.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MailTestMethodSecurityTest {

    @Test
    void mailOperatorCanUseMailTools() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            SystemSettingsProvider systemSettingsProvider = context.getBean(SystemSettingsProvider.class);
            SmtpRelayProbe smtpRelayProbe = context.getBean(SmtpRelayProbe.class);
            when(systemSettingsProvider.snapshot()).thenReturn(settings());
            when(smtpRelayProbe.probe(any())).thenReturn(smtpProbeResult());
            MockMvc mockMvc = mockMvc(context);

            mockMvc.perform(get("/api/v1/mail-test/test-smtp-config")
                            .principal(() -> "mail")
                            .requestAttr("user", user("mail", "MAIL_OPERATOR")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void runtimePolicyAdminCannotUseMailTools() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            MockMvc mockMvc = mockMvc(context);

            mockMvc.perform(get("/api/v1/mail-test/test-smtp-config")
                            .principal(() -> "runtime")
                            .requestAttr("user", user("runtime", "RUNTIME_POLICY_ADMIN")))
                    .andExpect(status().isForbidden());
        }
    }

    private static MockMvc mockMvc(AnnotationConfigApplicationContext context) {
        return MockMvcBuilders
                .standaloneSetup(context.getBean(MailTestController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserContextArgumentResolver())
                .build();
    }

    private static UserContext user(String userId, String role) {
        return UserContext.builder()
                .userId(userId)
                .username(userId)
                .roles(Set.of(role))
                .build();
    }

    private static SystemSettingsProvider.SystemSettingsSnapshot settings() {
        return new SystemSettingsProvider.SystemSettingsSnapshot(
                null,
                null,
                new SystemSettingsProvider.DeliverySettings(
                        "postfix",
                        new SystemSettingsProvider.PostfixSettings(
                                true,
                                "postfix",
                                10026,
                                10027,
                                10000,
                                ""),
                        null),
                null,
                null,
                null,
                null,
                null,
                List.of());
    }

    private static SmtpRelayProbe.SmtpProbeResult smtpProbeResult() {
        return new SmtpRelayProbe.SmtpProbeResult(
                "postfix",
                10026,
                DeliveryTransportProfile.SMTP_CLEAR,
                true,
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                false,
                List.of("PIPELINING"));
    }

    @EnableMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
    static class TestConfig {

        @Bean
        static DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
            DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
            creator.setProxyTargetClass(true);
            return creator;
        }

        @Bean
        MailTestController mailTestController(MailTestUseCase useCase) {
            return new MailTestController(useCase);
        }

        @Bean
        MailTestUseCase mailTestUseCase(OutboundMailSubmitter outboundMailSubmitter,
                                        CertificateRepository certificateRepository,
                                        SystemSettingsProvider systemSettingsProvider,
                                        RelayPolicyPort relayPolicyPort,
                                        SmtpRelayProbe smtpRelayProbe,
                                        MailMessageComposer mailMessageComposer,
                                        CryptoProfileSelector cryptoProfileSelector,
                                        DeliveryRouteResolver deliveryRouteResolver) {
            return new MailTestUseCase(
                    outboundMailSubmitter,
                    certificateRepository,
                    systemSettingsProvider,
                    relayPolicyPort,
                    smtpRelayProbe,
                    mailMessageComposer,
                    cryptoProfileSelector,
                    deliveryRouteResolver);
        }

        @Bean
        AppPermissionAuthorizer appPermissionAuthorizer(AppPermissionEvaluator evaluator) {
            return new AppPermissionAuthorizer(evaluator);
        }

        @Bean
        AppPermissionEvaluator appPermissionEvaluator() {
            return new AppPermissionEvaluator();
        }

        @Bean
        OutboundMailSubmitter outboundMailSubmitter() {
            return org.mockito.Mockito.mock(OutboundMailSubmitter.class);
        }

        @Bean
        CertificateRepository certificateRepository() {
            return org.mockito.Mockito.mock(CertificateRepository.class);
        }

        @Bean
        SystemSettingsProvider systemSettingsProvider() {
            return org.mockito.Mockito.mock(SystemSettingsProvider.class);
        }

        @Bean
        RelayPolicyPort relayPolicyPort() {
            RelayPolicyPort port = org.mockito.Mockito.mock(RelayPolicyPort.class);
            when(port.getProbeSettings()).thenReturn(new RelayPolicyPort.RelayProbeSettings(
                    false,
                    "",
                    0,
                    "",
                    "",
                    10000));
            return port;
        }

        @Bean
        SmtpRelayProbe smtpRelayProbe() {
            return org.mockito.Mockito.mock(SmtpRelayProbe.class);
        }

        @Bean
        MailMessageComposer mailMessageComposer() {
            return org.mockito.Mockito.mock(MailMessageComposer.class);
        }

        @Bean
        CryptoProfileSelector cryptoProfileSelector() {
            return org.mockito.Mockito.mock(CryptoProfileSelector.class);
        }

        @Bean
        DeliveryRouteResolver deliveryRouteResolver() {
            DeliveryRouteResolver resolver = org.mockito.Mockito.mock(DeliveryRouteResolver.class);
            when(resolver.resolve(any())).thenReturn(java.util.Optional.empty());
            return resolver;
        }
    }
}
