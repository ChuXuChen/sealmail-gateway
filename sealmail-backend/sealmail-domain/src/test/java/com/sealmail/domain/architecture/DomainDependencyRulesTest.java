package com.sealmail.domain.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainDependencyRulesTest {

    @Test
    void domainDoesNotImportUpperLayersOrInfrastructureFrameworks() throws IOException {
        List<Path> violations = javaFiles(Path.of("src/main/java")).stream()
                .filter(path -> containsAny(path,
                        "import com.sealmail.app.",
                        "import com.sealmail.web.",
                        "import com.sealmail.infra.",
                        "import jakarta.persistence.",
                        "import org.springframework.web.",
                        "import org.springframework.integration."))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Domain dependency violations: " + violations);
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
            for (String needle : needles) {
                if (content.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
