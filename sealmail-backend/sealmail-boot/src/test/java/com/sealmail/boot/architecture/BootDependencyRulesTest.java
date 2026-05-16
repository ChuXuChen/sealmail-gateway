package com.sealmail.boot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BootDependencyRulesTest {

    @Test
    void bootIsTheOnlyModuleDependingOnWebAndInfraTogether() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<artifactId>sealmail-web</artifactId>")
                        && pom.contains("<artifactId>sealmail-infra</artifactId>")
                        && pom.contains("<artifactId>sealmail-app</artifactId>"),
                "Boot module must compose web, app and infra modules");
    }
}
