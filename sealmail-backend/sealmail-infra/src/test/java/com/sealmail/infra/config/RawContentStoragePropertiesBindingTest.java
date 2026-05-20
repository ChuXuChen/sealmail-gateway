package com.sealmail.infra.config;

import com.sealmail.infra.config.properties.RawContentStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RawContentStoragePropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "sealmail.storage.raw-content.encryption.enabled=false",
                    "sealmail.storage.raw-content.encryption.key-ref=env:TEST_RAW_CONTENT_KEY"
            );

    @Test
    void bindsRawContentEncryptionProperties() {
        contextRunner.run(context -> {
            RawContentStorageProperties properties = context.getBean(RawContentStorageProperties.class);
            assertFalse(properties.getEncryption().isEnabled());
            assertEquals("env:TEST_RAW_CONTENT_KEY", properties.getEncryption().getKeyRef());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RawContentStorageProperties.class)
    static class TestConfig {
    }
}
