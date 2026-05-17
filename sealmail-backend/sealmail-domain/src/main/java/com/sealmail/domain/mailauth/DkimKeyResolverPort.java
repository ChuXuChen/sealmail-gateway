package com.sealmail.domain.mailauth;

import java.util.Optional;

public interface DkimKeyResolverPort {

    Optional<String> resolvePrivateKeyPem(DkimKeyRef keyRef);

    Optional<String> resolvePublicKeyData(DkimKeyRef keyRef);
}
