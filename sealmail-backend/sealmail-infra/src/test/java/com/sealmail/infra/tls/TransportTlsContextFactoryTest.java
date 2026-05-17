package com.sealmail.infra.tls;

import com.sealmail.infra.config.properties.TransportTlsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSocket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportTlsContextFactoryTest {

    private static final List<String> KONA_SYSTEM_PROPERTIES = List.of(
            "com.tencent.kona.ssl.namedGroups",
            "com.tencent.kona.ssl.client.signatureSchemes",
            "com.tencent.kona.ssl.server.signatureSchemes"
    );

    @AfterEach
    void clearKonaSystemProperties() {
        KONA_SYSTEM_PROPERTIES.forEach(System::clearProperty);
    }

    @Test
    void jdkEngineKeepsDefaultJvmTlsBehavior() {
        TransportTlsContextFactory factory = new TransportTlsContextFactory(new TransportTlsProperties());

        assertEquals(TransportTlsProperties.Engine.JDK, factory.engine());
        assertEquals("JDK", factory.effectiveProvider());
        assertEquals("TLS", factory.effectiveProtocol());
        assertTrue(factory.effectiveEnabledProtocols().isEmpty());
        assertTrue(factory.effectiveEnabledCipherSuites().isEmpty());
    }

    @Test
    void rfc8998EngineCreatesKonaTls13SmSocket() throws Exception {
        TransportTlsProperties properties = new TransportTlsProperties();
        properties.setEngine(TransportTlsProperties.Engine.KONA_RFC8998_TLS);
        TransportTlsContextFactory factory = new TransportTlsContextFactory(properties);

        assertEquals("Kona", factory.effectiveProvider());
        assertEquals("TLS", factory.effectiveProtocol());
        assertEquals(List.of("TLSv1.3"), factory.effectiveEnabledProtocols());
        assertEquals(List.of("TLS_SM4_GCM_SM3"), factory.effectiveEnabledCipherSuites());

        try (SSLSocket socket = factory.createClientSocket()) {
            assertEquals(List.of("TLSv1.3"), List.of(socket.getEnabledProtocols()));
            assertEquals(List.of("TLS_SM4_GCM_SM3"), List.of(socket.getEnabledCipherSuites()));
        }
    }

    @Test
    void tlcpEngineCreatesKonaTlcpSocket() throws Exception {
        TransportTlsProperties properties = new TransportTlsProperties();
        properties.setEngine(TransportTlsProperties.Engine.KONA_TLCP);
        TransportTlsContextFactory factory = new TransportTlsContextFactory(properties);

        assertEquals("Kona", factory.effectiveProvider());
        assertEquals("TLCP", factory.effectiveProtocol());
        assertEquals(List.of("TLCPv1.1"), factory.effectiveEnabledProtocols());
        assertEquals(List.of("TLCP_ECC_SM4_GCM_SM3", "TLCP_ECDHE_SM4_GCM_SM3"),
                factory.effectiveEnabledCipherSuites());

        try (SSLSocket socket = factory.createClientSocket()) {
            assertEquals(List.of("TLCPv1.1"), List.of(socket.getEnabledProtocols()));
            assertEquals(List.of("TLCP_ECC_SM4_GCM_SM3", "TLCP_ECDHE_SM4_GCM_SM3"),
                    List.of(socket.getEnabledCipherSuites()));
        }
    }
}
