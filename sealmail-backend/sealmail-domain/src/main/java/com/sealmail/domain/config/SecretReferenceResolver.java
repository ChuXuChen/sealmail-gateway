package com.sealmail.domain.config;

public interface SecretReferenceResolver {

    String resolve(String secretRef);
}
