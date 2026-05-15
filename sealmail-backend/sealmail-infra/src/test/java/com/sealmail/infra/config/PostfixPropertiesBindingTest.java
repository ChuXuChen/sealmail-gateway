package com.sealmail.infra.config;

import com.sealmail.infra.config.properties.PostfixProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostfixPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "sealmail.postfix.enabled=true",
                    "sealmail.postfix.host=127.0.0.1",
                    "sealmail.postfix.after-filter-port=10026",
                    "sealmail.postfix.outbound-port=10027",
                    "sealmail.postfix.timeout=12000"
            );

    @Test
    void bindsPostfixIntegrationProperties() {
        contextRunner.run(context -> {
            PostfixProperties properties = context.getBean(PostfixProperties.class);
            assertTrue(properties.isEnabled());
            assertEquals("127.0.0.1", properties.getHost());
            assertEquals(10026, properties.getAfterFilterPort());
            assertEquals(10027, properties.getOutboundPort());
            assertEquals(12000, properties.getTimeout());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PostfixProperties.class)
    static class TestConfig {
    }
}
