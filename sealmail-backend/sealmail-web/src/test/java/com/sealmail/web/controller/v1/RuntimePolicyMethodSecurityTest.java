package com.sealmail.web.controller.v1;

import com.sealmail.app.security.AppPermissionAuthorizer;
import com.sealmail.app.security.AppPermissionEvaluator;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.config.ManageRuntimePolicyUseCase;
import com.sealmail.domain.config.GmEdgePolicyPort;
import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.config.RelayPolicyPort;
import com.sealmail.domain.config.SmimeSuitePolicyPort;
import com.sealmail.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuntimePolicyMethodSecurityTest {

    @Test
    void runtimePolicyRoleCanReadRuntimePolicies() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            RelayPolicyPort relayPolicyPort = context.getBean(RelayPolicyPort.class);
            when(relayPolicyPort.getSettings()).thenReturn(relaySettings());
            MockMvc mockMvc = mockMvc(context);

            mockMvc.perform(get("/api/v1/runtime-policies/relay")
                            .principal(() -> "runtime")
                            .requestAttr("user", user("runtime", "RUNTIME_POLICY_ADMIN")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void mailOperatorCannotReadRuntimePolicies() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            MockMvc mockMvc = mockMvc(context);

            mockMvc.perform(get("/api/v1/runtime-policies/relay")
                            .principal(() -> "mail")
                            .requestAttr("user", user("mail", "MAIL_OPERATOR")))
                    .andExpect(status().isForbidden());
        }
    }

    private static MockMvc mockMvc(AnnotationConfigApplicationContext context) {
        return MockMvcBuilders
                .standaloneSetup(context.getBean(RuntimePolicyController.class))
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

    private static RelayPolicyPort.RelayPolicySettings relaySettings() {
        return new RelayPolicyPort.RelayPolicySettings(
                true,
                "relay.example",
                587,
                "relay-user",
                false,
                null,
                10000,
                "",
                false,
                Instant.now());
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
        RuntimePolicyController runtimePolicyController(ManageRuntimePolicyUseCase useCase) {
            return new RuntimePolicyController(useCase);
        }

        @Bean
        ManageRuntimePolicyUseCase manageRuntimePolicyUseCase(RelayPolicyPort relayPolicyPort,
                                                              QuarantinePolicyPort quarantinePolicyPort,
                                                              GmEdgePolicyPort gmEdgePolicyPort,
                                                              SmimeSuitePolicyPort smimeSuitePolicyPort) {
            return new ManageRuntimePolicyUseCase(
                    relayPolicyPort,
                    quarantinePolicyPort,
                    gmEdgePolicyPort,
                    smimeSuitePolicyPort);
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
        RelayPolicyPort relayPolicyPort() {
            return org.mockito.Mockito.mock(RelayPolicyPort.class);
        }

        @Bean
        QuarantinePolicyPort quarantinePolicyPort() {
            return org.mockito.Mockito.mock(QuarantinePolicyPort.class);
        }

        @Bean
        GmEdgePolicyPort gmEdgePolicyPort() {
            GmEdgePolicyPort port = org.mockito.Mockito.mock(GmEdgePolicyPort.class);
            when(port.getSettings()).thenReturn(gmEdgeSettings());
            return port;
        }

        @Bean
        SmimeSuitePolicyPort smimeSuitePolicyPort() {
            SmimeSuitePolicyPort port = org.mockito.Mockito.mock(SmimeSuitePolicyPort.class);
            when(port.getSettings()).thenReturn(new SmimeSuitePolicyPort.SmimeSuitePolicySettings(
                    "RSA-AES256",
                    "SM2-SM4",
                    List.of(),
                    List.of(),
                    Instant.now()));
            return port;
        }

        private static GmEdgePolicyPort.GmEdgePolicySettings gmEdgeSettings() {
            return new GmEdgePolicyPort.GmEdgePolicySettings(
                    false,
                    new GmEdgePolicyPort.InboundSettings(false, "0.0.0.0", 2525, 2465, 50, 100),
                    new GmEdgePolicyPort.OutboundSettings(false, "0.0.0.0", 2526, 50, 100),
                    new GmEdgePolicyPort.PostfixSettings("postfix", 2530),
                    new GmEdgePolicyPort.TlsSettings(
                            List.of(),
                            List.of(),
                            "",
                            false,
                            null,
                            "PKCS12",
                            "",
                            false,
                            null,
                            "PKCS12",
                            false),
                    new GmEdgePolicyPort.LimitsSettings(10000, 10000, 26214400, 8192, 100),
                    List.of(),
                    Instant.now());
        }
    }
}
