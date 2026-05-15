package com.sealmail.infra.config;

import com.sealmail.infra.config.properties.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecurityPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "sealmail.security.crl.enabled=false",
                    "sealmail.security.ocsp.enabled=false",
                    "sealmail.security.ocsp.timeout=2500"
            );

    @Test
    void bindsNestedCertificateValidationProperties() {
        contextRunner.run(context -> {
            SecurityProperties properties = context.getBean(SecurityProperties.class);
            assertFalse(properties.isCrlEnabled());
            assertFalse(properties.isOcspEnabled());
            assertEquals(2500, properties.getOcspTimeout());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class TestConfig {
    }
}
