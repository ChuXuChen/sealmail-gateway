package com.sealmail.edge.tls;

import com.tencent.kona.KonaProvider;
import com.tencent.kona.crypto.KonaCryptoProvider;
import com.tencent.kona.pkix.KonaPKIXProvider;
import com.tencent.kona.ssl.KonaSSLProvider;

import java.security.Provider;
import java.security.Security;
import java.util.List;

public final class KonaSecurity {
    private static final List<Provider> PROVIDERS = List.of(
            new KonaProvider(),
            new KonaCryptoProvider(),
            new KonaPKIXProvider(),
            new KonaSSLProvider()
    );

    private KonaSecurity() {
    }

    public static void registerProviders() {
        for (Provider provider : PROVIDERS) {
            registerProvider(provider);
        }
    }

    private static void registerProvider(Provider provider) {
        if (Security.getProvider(provider.getName()) == null) {
            Security.addProvider(provider);
        }
    }
}
