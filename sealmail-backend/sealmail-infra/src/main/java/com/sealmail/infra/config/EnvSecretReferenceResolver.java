package com.sealmail.infra.config;

import com.sealmail.domain.config.SecretReferenceResolver;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class EnvSecretReferenceResolver implements SecretReferenceResolver {

    private static final String ENV_PREFIX = "env:";
    private static final String FILE_PREFIX = "file:";

    private final Environment environment;

    public EnvSecretReferenceResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String resolve(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return null;
        }
        String trimmed = secretRef.trim();
        if (trimmed.startsWith(ENV_PREFIX)) {
            return environment.getProperty(trimmed.substring(ENV_PREFIX.length()));
        }
        if (trimmed.startsWith(FILE_PREFIX)) {
            try {
                return Files.readString(Path.of(trimmed.substring(FILE_PREFIX.length())));
            } catch (Exception e) {
                return null;
            }
        }
        return environment.getProperty(trimmed);
    }
}
