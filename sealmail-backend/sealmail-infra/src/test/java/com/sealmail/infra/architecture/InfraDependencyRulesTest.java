package com.sealmail.infra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InfraDependencyRulesTest {

    @Test
    void infraDoesNotImportAppOrWebLayers() throws IOException {
        List<Path> violations = javaFiles(Path.of("src/main/java")).stream()
                .filter(path -> containsAny(path,
                        "import com.sealmail.app.",
                        "import com.sealmail.web.",
                        "import org.springframework.web.",
                        "import jakarta.servlet."))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Infra dependency violations: " + violations);
    }

    @Test
    void infraPomDoesNotDependOnAppOrWebModules() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(!pom.contains("<artifactId>sealmail-app</artifactId>")
                        && !pom.contains("<artifactId>sealmail-web</artifactId>")
                        && !pom.contains("<artifactId>spring-boot-starter-web</artifactId>"),
                "Infra module must not depend on app, web or web runtime modules");
    }

    @Test
    void infraDoesNotDefineApplicationEntrypointOrGlobalScan() throws IOException {
        List<Path> violations = javaFiles(Path.of("src/main/java")).stream()
                .filter(path -> containsAny(path,
                        "@SpringBootApplication",
                        "@ComponentScan(basePackages = \"com.sealmail\")",
                        "@ComponentScan({\"com.sealmail\"}",
                        "scanBasePackages = \"com.sealmail\""))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Infra startup or global scan violations: " + violations);
    }

    @Test
    void mailPipelineDoesNotUseLegacyMailAuthImplementation() throws IOException {
        List<Path> violations = javaFiles(Path.of("src/main/java/com/sealmail/infra/mail/pipeline")).stream()
                .filter(path -> containsAny(path, "com.sealmail.infra.mail.auth."))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Mail pipeline must use mailauth ports/adapters: " + violations);
    }

    @Test
    void modernMailAuthDoesNotUseLegacyMailAuthImplementation() throws IOException {
        List<Path> violations = javaFiles(Path.of("src/main/java/com/sealmail/infra/mailauth")).stream()
                .filter(path -> containsAny(path, "com.sealmail.infra.mail.auth."))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Modern mailauth adapters must not depend on legacy mail auth: " + violations);
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
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
}
