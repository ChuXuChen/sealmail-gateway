package com.sealmail.web.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebDependencyRulesTest {

    @Test
    void webDoesNotImportDomainOrInfraImplementations() throws IOException {
        List<Path> violations = javaFiles(Path.of("src/main/java")).stream()
                .filter(path -> containsAny(path,
                        "import com.sealmail.domain.",
                        "import com.sealmail.infra.",
                        "com.sealmail.domain.",
                        "com.sealmail.infra.",
                        "import org.bouncycastle.",
                        "import io.jsonwebtoken.",
                        "import jakarta.persistence.",
                        "import org.springframework.integration.",
                        "import java.security.cert.",
                        "import java.security.KeyPair",
                        "import java.security.PrivateKey",
                        "import java.security.PublicKey",
                        "import java.util.Base64",
                        "import java.nio.charset.StandardCharsets",
                        "java.nio.file.Files",
                        "java.nio.file.Path",
                        "java.nio.file.Paths",
                        "MIME-Version:",
                        "Content-Transfer-Encoding:",
                        "Content-Type:"))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Web dependency violations: " + violations);
    }

    @Test
    void webPomDoesNotDependOnInfraModule() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(!containsAny(pom,
                        "<artifactId>sealmail-infra</artifactId>",
                        "<artifactId>sealmail-domain</artifactId>",
                        "<artifactId>bcprov-jdk18on</artifactId>",
                        "<artifactId>bcpkix-jdk18on</artifactId>",
                        "<artifactId>bcjmail-jdk18on</artifactId>",
                        "<artifactId>jjwt-api</artifactId>",
                        "<artifactId>jjwt-impl</artifactId>",
                        "<artifactId>spring-boot-starter-data-jpa</artifactId>",
                        "<artifactId>spring-integration-core</artifactId>",
                        "<artifactId>postgresql</artifactId>",
                        "<artifactId>flyway-core</artifactId>"),
                "Web module must depend only on app and presentation libraries");
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
