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
    void pipelineStepsUseTypedMessageContextHelper() throws IOException {
        Path stepsRoot = BACKEND_ROOT.resolve("sealmail-infra/src/main/java/com/sealmail/infra/mail/pipeline/step");
        List<Path> violations = productionJavaFiles(stepsRoot).stream()
                .filter(path -> containsAny(path, "MailProcessingHeaders.CONTEXT"))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Pipeline step context header violations: " + violations);
    }

    @Test
    void mailIntegrationFlowsRemainsChannelAssemblyFacade() throws IOException {
        Path flowFacade = BACKEND_ROOT.resolve(
                "sealmail-infra/src/main/java/com/sealmail/infra/mail/pipeline/MailIntegrationFlows.java");
        List<String> violations = List.of(
                        "MailProcessingContext",
                        "MailProcessingException",
                        "ProcessingResult",
                        "requiresQuarantine",
                        "completeProcessing",
                        "MessageBuilder",
                        "MailProcessingErrorType")
                .stream()
                .filter(needle -> containsAny(flowFacade, needle))
                .toList();

        assertTrue(violations.isEmpty(), () -> "MailIntegrationFlows owns business state again: " + violations);
    }

    @Test
    void productionCodeAndConfigDoNotAllowWildcardCorsOrigins() throws IOException {
        List<Path> violations = Files.walk(BACKEND_ROOT)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String normalized = path.normalize().toString().replace('\\', '/');
                    return (normalized.contains("/src/main/java/") && normalized.endsWith(".java"))
                            || (normalized.contains("/src/main/resources/") && isConfigPath(path));
                })
                .filter(Phase10HardeningRulesTest::containsWildcardCorsOrigin)
                .toList();

        assertTrue(violations.isEmpty(), () -> "Wildcard CORS origin violations: " + violations);
    }

    @Test
    void productionConfigDoesNotExposeHealthDetailsOrSqlOutput() throws IOException {
        List<Path> violations = mainResourceConfigFiles().stream()
                .filter(Phase10HardeningRulesTest::containsUnsafeProductionOperationalConfig)
                .toList();

        assertTrue(violations.isEmpty(), () -> "Unsafe production operational config violations: " + violations);
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

    private static List<Path> mainResourceConfigFiles() throws IOException {
        try (var stream = Files.walk(BACKEND_ROOT)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("/src/main/resources/"))
                    .filter(Phase10HardeningRulesTest::isConfigPath)
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

    private static boolean containsWildcardCorsOrigin(Path path) {
        String content = read(path);
        String compact = content.replace(" ", "").replace("\t", "");
        Pattern yamlOrigin = Pattern.compile("allowed-origin(?:s|-patterns):\\s*['\"]?\\*['\"]?");
        Pattern envFallbackOrigin = Pattern.compile("allowed-origin(?:s|-patterns):\\s*\\$\\{[^}:]+:\\*}");
        return yamlOrigin.matcher(content).find()
                || envFallbackOrigin.matcher(content).find()
                || containsAny(compact,
                "allowedOrigins=List.of(\"*\")",
                "setAllowedOrigins(List.of(\"*\")",
                "addAllowedOrigin(\"*\")",
                "allowedOriginPatterns=List.of(\"*\")",
                "setAllowedOriginPatterns(List.of(\"*\")",
                "addAllowedOriginPattern(\"*\")");
    }

    private static boolean containsUnsafeProductionOperationalConfig(Path path) {
        String fileName = path.getFileName().toString();
        String content = read(path);
        if (fileName.matches("application-prod\\.(ya?ml|properties)")) {
            return containsHealthOrSqlExposure(content);
        }
        if (fileName.matches("application\\.ya?ml")) {
            return Pattern.compile("(?m)^---\\s*$")
                    .splitAsStream(content)
                    .filter(Phase10HardeningRulesTest::isProductionProfileSection)
                    .anyMatch(Phase10HardeningRulesTest::containsHealthOrSqlExposure);
        }
        return false;
    }

    private static boolean isProductionProfileSection(String section) {
        return containsAny(section,
                "on-profile: prod",
                "on-profile: \"prod\"",
                "on-profile: 'prod'",
                "on-profile=prod");
    }

    private static boolean containsHealthOrSqlExposure(String content) {
        String normalized = content.toLowerCase(java.util.Locale.ROOT);
        return containsAny(normalized,
                "show-details: always",
                "show-details=always",
                "management.endpoint.health.show-details=always",
                "show-sql: true",
                "show-sql=true",
                "hibernate.show_sql: true",
                "hibernate.show_sql=true",
                "org.hibernate.sql: debug",
                "org.hibernate.sql: trace",
                "org.hibernate.sql=debug",
                "org.hibernate.sql=trace");
    }

    private static boolean isConfigPath(Path path) {
        String normalized = path.normalize().toString().replace('\\', '/');
        return containsAny(normalized, ".yml", ".yaml", ".properties");
    }

    private static boolean containsAny(Path path, String... needles) {
        return containsAny(read(path), needles);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
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
