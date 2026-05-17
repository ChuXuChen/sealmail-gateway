package com.sealmail.infra.mailauth;

import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.domain.mailauth.DkimKeyRef;
import com.sealmail.domain.mailauth.DkimKeyResolverPort;
import com.sealmail.infra.crypto.util.PemUtils;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Optional;

@Component
public class SecretRefDkimKeyResolver implements DkimKeyResolverPort {

    private final SecretReferenceResolver secretReferenceResolver;

    public SecretRefDkimKeyResolver(SecretReferenceResolver secretReferenceResolver) {
        this.secretReferenceResolver = secretReferenceResolver;
    }

    @Override
    public Optional<String> resolvePrivateKeyPem(DkimKeyRef keyRef) {
        if (keyRef == null || !keyRef.configured()) {
            return Optional.empty();
        }
        try {
            if (hasText(keyRef.secretRef())) {
                return Optional.ofNullable(secretReferenceResolver.resolve(keyRef.secretRef()))
                        .filter(this::hasText);
            }
            if (hasText(keyRef.path())) {
                return Optional.of(Files.readString(Path.of(keyRef.path()))).filter(this::hasText);
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> resolvePublicKeyData(DkimKeyRef keyRef) {
        return resolvePrivateKeyPem(keyRef).flatMap(this::publicKeyData);
    }

    private Optional<String> publicKeyData(String keyPem) {
        try {
            PrivateKey privateKey = PemUtils.parsePrivateKey(keyPem, null);
            if (!(privateKey instanceof RSAPrivateCrtKey rsa)) {
                return Optional.empty();
            }
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent());
            byte[] der = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec).getEncoded();
            return Optional.of(Base64.getEncoder().encodeToString(der));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
