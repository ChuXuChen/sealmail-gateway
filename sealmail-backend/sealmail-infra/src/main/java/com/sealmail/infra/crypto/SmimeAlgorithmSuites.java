package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.SMIMEEncryptionSuite;
import com.sealmail.infra.config.properties.SmimeCryptoProperties;

import java.util.EnumMap;
import java.util.Map;

public class SmimeAlgorithmSuites {

    private final Map<SMIMEEncryptionSuite, SmimeAlgorithmSuite> suites =
            new EnumMap<>(SMIMEEncryptionSuite.class);

    public SmimeAlgorithmSuites(SmimeCryptoProperties properties) {
        suites.put(SMIMEEncryptionSuite.STANDARD,
                SmimeAlgorithmSuite.from(SMIMEEncryptionSuite.STANDARD, properties.getStandard()));
        suites.put(SMIMEEncryptionSuite.GM,
                SmimeAlgorithmSuite.from(SMIMEEncryptionSuite.GM, properties.getGm()));
    }

    public static SmimeAlgorithmSuites defaults() {
        return new SmimeAlgorithmSuites(new SmimeCryptoProperties());
    }

    public SmimeAlgorithmSuite get(SMIMEEncryptionSuite suite) {
        SmimeAlgorithmSuite algorithmSuite = suites.get(suite);
        if (algorithmSuite == null) {
            throw new IllegalArgumentException("No S/MIME algorithm suite configured for " + suite);
        }
        return algorithmSuite;
    }
}
