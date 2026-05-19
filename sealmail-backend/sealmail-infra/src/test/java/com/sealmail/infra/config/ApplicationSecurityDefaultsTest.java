package com.sealmail.infra.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ApplicationSecurityDefaultsTest {

    @Test
    void defaultDocumentDoesNotExposeHealthDetailsOrSqlLogging() throws IOException {
        Map<String, Object> document = applicationDocuments().getFirst();

        assertEquals("never", nestedValue(document, "management", "endpoint", "health", "show-details"));
        assertEquals(false, nestedValue(document, "spring", "jpa", "show-sql"));
        assertNotEquals("86400", nestedValue(document, "jwt", "token-validity-in-seconds"));
    }

    @Test
    void postgresProfileDoesNotEnableSqlLogging() throws IOException {
        Map<String, Object> postgres = applicationDocuments().stream()
                .filter(document -> "postgres".equals(nestedValue(document, "spring", "config", "activate", "on-profile")))
                .findFirst()
                .orElseThrow();

        assertEquals(false, nestedValue(postgres, "spring", "jpa", "show-sql"));
    }

    @Test
    void productionProfileKeepsHealthDetailsHidden() throws IOException {
        Map<String, Object> prod = applicationDocuments().stream()
                .filter(document -> "prod".equals(nestedValue(document, "spring", "config", "activate", "on-profile")))
                .findFirst()
                .orElseThrow();

        assertEquals("never", nestedValue(prod, "management", "endpoint", "health", "show-details"));
        assertEquals("${SEALMAIL_CORS_ALLOWED_ORIGINS:}",
                nestedValue(prod, "sealmail", "web", "security", "cors", "allowed-origins"));
    }

    @Test
    void devAndTestProfilesCanExposeHealthDetails() throws IOException {
        for (String profile : List.of("dev", "test")) {
            Map<String, Object> document = applicationDocuments().stream()
                    .filter(item -> profile.equals(nestedValue(item, "spring", "config", "activate", "on-profile")))
                    .findFirst()
                    .orElseThrow();

            assertEquals("always", nestedValue(document, "management", "endpoint", "health", "show-details"));
        }
    }

    private static List<Map<String, Object>> applicationDocuments() throws IOException {
        Path applicationYaml = Path.of("src/main/resources/application.yml");
        try (InputStream input = Files.newInputStream(applicationYaml)) {
            Yaml yaml = new Yaml();
            List<Map<String, Object>> documents = new ArrayList<>();
            for (Object document : yaml.loadAll(input)) {
                if (document instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    documents.add(typed);
                }
            }
            return documents;
        }
    }

    private static Object nestedValue(Map<String, Object> map, String... path) {
        Object value = map;
        for (String segment : path) {
            if (!(value instanceof Map<?, ?> nested)) {
                return null;
            }
            value = nested.get(segment);
        }
        return value;
    }
}
