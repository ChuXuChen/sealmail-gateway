package com.sealmail.infra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase10HardeningRulesTest {

    private static final Path REPO_ROOT = Path.of("../..").normalize();
    private static final Path BACKEND_ROOT = Path.of("..").normalize();

    @Test
    void productionCodeDoesNotReintroduceOldPipelineAbstractionsOrStaticStepState() throws IOException {
        List<Path> violations = productionJavaFiles(BACKEND_ROOT).stream()
                .filter(path -> containsAny(path,
                        "PipelineResult",
                        "MailPipelineStep",
                        "PipelineStepTracker",
                        "MailPipelineFlow",
                        "sendError(",
                        "executeWithTracking",
                        "executeMessageWithTracking",
                        "ConcurrentHashMap.newKeySet",
                        "Collections.synchronizedSet"))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Old pipeline pattern violations: " + violations);
    }

    @Test
    void productionCodeDoesNotUseStringClassNameReflection() throws IOException {
        List<Path> violations = productionJavaFiles(BACKEND_ROOT).stream()
                .filter(path -> containsAny(path,
                        "Class.forName(",
                        "getDeclaredFields(",
                        "setAccessible(true)"))
                .toList();

        assertTrue(violations.isEmpty(), () -> "String/reflection bypass violations: " + violations);
    }

    @Test
    void productionCodeDoesNotExposeRawCryptoTestEntrypoints() throws IOException {
        List<Path> violations = productionJavaFiles(BACKEND_ROOT).stream()
                .filter(path -> containsAny(path,
                        "CryptoCapabilityTestController",
                        "SMIMEController",
                        "SmimeOperationUseCase",
                        "SmimeMessageCryptoPort",
                        "SmimeMessageCryptoAdapter",
                        "CryptoKeyMaterialResponse",
                        "SmimeSignatureValidationResponse",
                        "generateTestMaterial(",
                        "/api/v1/crypto-test",
                        "/api/v1/smime"))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Raw crypto test entrypoint violations: " + violations);
    }

    @Test
    void productionCodeDoesNotCarryStandaloneKeyGenerationUtilities() throws IOException {
        List<Path> violations = productionJavaFiles(BACKEND_ROOT).stream()
                .filter(path -> containsAny(path,
                        "System.out.println(",
                        "privateKeyToPEM(",
                        "class SM2KeyGenerator"))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Standalone key generation utility violations: " + violations);
    }

    @Test
    void springIntegrationBusinessHeadersRemainStronglyTyped() throws IOException {
        List<Path> violations = productionJavaFiles(BACKEND_ROOT).stream()
                .filter(path -> containsAny(path,
                        "setHeader(\"mailEnvelope\"",
                        "setHeader(\"processingId\"",
                        "setHeader(\"preferredAlgorithm\"",
                        "setHeader(\"mustEncrypt\"",
                        "setHeader(\"quarantineRequired\"",
                        "setHeader(\"relayHost\"",
                        "get(\"mailEnvelope\"",
                        "get(\"preferredAlgorithm\"",
                        "get(\"quarantineRequired\"",
                        "get(\"relayHost\""))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Legacy mail header violations: " + violations);
    }

    @Test
    void runtimeRelayPolicyDoesNotUseYamlFallback() throws IOException {
        List<Path> codeViolations = productionJavaFiles(BACKEND_ROOT).stream()
                .filter(path -> containsAny(path,
                        "RelayProperties",
                        "sealmail.relay",
                        "SEALMAIL_RELAY_HOST",
                        "SEALMAIL_RELAY_PORT",
                        "SEALMAIL_RELAY_USERNAME",
                        "SEALMAIL_RELAY_PASSWORD_SECRET_REF",
                        "SEALMAIL_RELAY_ENVELOPE_FROM"))
                .toList();
        List<Path> configViolations = Files.walk(BACKEND_ROOT)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().contains("/src/main/resources/"))
                .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                .filter(path -> containsAny(path,
                        "  relay:",
                        "SEALMAIL_RELAY_HOST",
                        "SEALMAIL_RELAY_PORT",
                        "SEALMAIL_RELAY_USERNAME",
                        "SEALMAIL_RELAY_PASSWORD_SECRET_REF",
                        "SEALMAIL_RELAY_ENVELOPE_FROM"))
                .toList();

        assertTrue(codeViolations.isEmpty(), () -> "Relay YAML fallback code violations: " + codeViolations);
        assertTrue(configViolations.isEmpty(), () -> "Relay YAML fallback config violations: " + configViolations);
    }

    @Test
    void runtimeSettingsDoNotAdvertiseRemovedYamlFallback() throws IOException {
        List<Path> violations = productionJavaFiles(BACKEND_ROOT).stream()
                .filter(path -> containsAny(path,
                        "application.yml deployment fallback",
                        "YAML fallback",
                        "yaml fallback"))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Removed YAML fallback is still advertised: " + violations);
    }

    @Test
    void migrationsDropLegacyDkimPlaintextKeyColumnForward() throws IOException {
        List<Path> migrations = migrationFiles();
        int lastPemMention = migrations.stream()
                .filter(path -> containsAny(path, "dkim_private_key_pem"))
                .mapToInt(Phase10HardeningRulesTest::flywayVersion)
                .max()
                .orElse(-1);
        int lastPemDrop = migrations.stream()
                .filter(path -> containsAny(path, "DROP COLUMN IF EXISTS dkim_private_key_pem"))
                .mapToInt(Phase10HardeningRulesTest::flywayVersion)
                .max()
                .orElse(-1);

        assertTrue(lastPemMention < 0 || lastPemDrop >= lastPemMention,
                () -> "Legacy DKIM PEM column is mentioned after its last forward drop migration");
    }

    @Test
    void repositoryDoesNotContainPlainSensitiveMaterialFiles() throws IOException {
        List<Path> violations = Files.walk(REPO_ROOT)
                .filter(Files::isRegularFile)
                .filter(path -> !isIgnoredPath(path))
                .filter(Phase10HardeningRulesTest::isTextLikePath)
                .filter(Phase10HardeningRulesTest::containsPemMaterialBlock)
                .toList();

        assertTrue(violations.isEmpty(), () -> "Plain sensitive material found: " + violations);
    }

    private static List<Path> productionJavaFiles(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static List<Path> migrationFiles() throws IOException {
        try (var stream = Files.walk(BACKEND_ROOT)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("/src/main/resources/db/migration/"))
                    .filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .toList();
        }
    }

    private static int flywayVersion(Path path) {
        var matcher = Pattern.compile("V(\\d+)__").matcher(path.getFileName().toString());
        if (!matcher.find()) {
            return -1;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean containsAny(Path path, String... needles) {
        try {
            String content = Files.readString(path);
            return containsAny(content, needles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean containsAny(String content, String... needles) {
        for (String needle : needles) {
            if (content.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIgnoredPath(Path path) {
        String normalized = path.normalize().toString().replace('\\', '/');
        return containsAny(normalized,
                "/.git/",
                "/.idea/",
                "/node_modules/",
                "/target/",
                "/dist/");
    }

    private static boolean containsPemMaterialBlock(Path path) {
        Pattern privateKey = Pattern.compile(
                "-----BEGIN (?:RSA |EC |ENCRYPTED |OPENSSH |DSA |PRIVATE )?PRIVATE KEY-----\\s+"
                        + "[A-Za-z0-9+/=\\r\\n]{80,}\\s+"
                        + "-----END (?:RSA |EC |ENCRYPTED |OPENSSH |DSA |PRIVATE )?PRIVATE KEY-----",
                Pattern.DOTALL);
        Pattern certificate = Pattern.compile(
                "-----BEGIN CERTIFICATE-----\\s+[A-Za-z0-9+/=\\r\\n]{200,}\\s+-----END CERTIFICATE-----",
                Pattern.DOTALL);
        try {
            String content = Files.readString(path);
            return privateKey.matcher(content).find() || certificate.matcher(content).find();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isTextLikePath(Path path) {
        String normalized = path.normalize().toString().replace('\\', '/');
        return containsAny(normalized,
                ".java",
                ".ts",
                ".tsx",
                ".js",
                ".jsx",
                ".json",
                ".xml",
                ".yml",
                ".yaml",
                ".properties",
                ".sql",
                ".md",
                ".sh",
                ".css",
                ".html",
                ".svg");
    }
}
