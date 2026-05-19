package com.sealmail.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebSecurityPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void corsDefaultsOnlyAllowLocalDevelopmentOrigins() {
        contextRunner.run(context -> {
            WebSecurityProperties properties = context.getBean(WebSecurityProperties.class);

            assertEquals(List.of("http://localhost:5173", "http://127.0.0.1:5173"),
                    properties.getCors().getAllowedOrigins());
            assertFalse(properties.getCors().getAllowedOrigins().contains("*"));
        });
    }

    @Test
    void corsConfigurationUsesConfiguredAllowlist() {
        contextRunner
                .withPropertyValues(
                        "sealmail.web.security.cors.allowed-origins=https://admin.example.com,https://ops.example.com",
                        "sealmail.web.security.cors.allowed-methods=GET,POST",
                        "sealmail.web.security.cors.allowed-headers=Authorization,Content-Type",
                        "sealmail.web.security.cors.max-age=120")
                .run(context -> {
                    WebSecurityProperties properties = context.getBean(WebSecurityProperties.class);
                    SecurityConfig securityConfig = new SecurityConfig(null, properties);
                    UrlBasedCorsConfigurationSource source =
                            (UrlBasedCorsConfigurationSource) securityConfig.corsConfigurationSource();
                    CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/"));

                    assertEquals(List.of("https://admin.example.com", "https://ops.example.com"),
                            configuration.getAllowedOrigins());
                    assertEquals(List.of("GET", "POST"), configuration.getAllowedMethods());
                    assertEquals(List.of("Authorization", "Content-Type"), configuration.getAllowedHeaders());
                    assertEquals(120L, configuration.getMaxAge());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WebSecurityProperties.class)
    static class TestConfig {
    }
}
