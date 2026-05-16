package com.sealmail.infra.config;

import com.sealmail.infra.config.properties.SmtpServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmtpServerPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "sealmail.smtp.server.port=10025",
                    "sealmail.smtp.server.bind-address=127.0.0.1",
                    "sealmail.smtp.server.max-connections=42",
                    "sealmail.smtp.server.keystore-password-secret-ref=env:SMTP_KEYSTORE_PASSWORD",
                    "sealmail.smtp.server.key-password-secret-ref=env:SMTP_KEY_PASSWORD",
                    "sealmail.smtp.server.private-key-password-secret-ref=env:SMTP_PRIVATE_KEY_PASSWORD"
            );

    @Test
    void bindsSmtpServerPropertiesFromServerPrefix() {
        contextRunner.run(context -> {
            SmtpServerProperties properties = context.getBean(SmtpServerProperties.class);
            assertEquals(10025, properties.getPort());
            assertEquals("127.0.0.1", properties.getBindAddress());
            assertEquals(42, properties.getMaxConnections());
            assertEquals("env:SMTP_KEYSTORE_PASSWORD", properties.getKeystorePasswordSecretRef());
            assertEquals("env:SMTP_KEY_PASSWORD", properties.getKeyPasswordSecretRef());
            assertEquals("env:SMTP_PRIVATE_KEY_PASSWORD", properties.getPrivateKeyPasswordSecretRef());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SmtpServerProperties.class)
    static class TestConfig {
    }
}
