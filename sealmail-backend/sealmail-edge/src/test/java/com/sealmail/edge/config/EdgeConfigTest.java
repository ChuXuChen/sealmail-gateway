package com.sealmail.edge.config;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeConfigTest {
    @Test
    void parsesDefaults() {
        EdgeConfig config = EdgeConfig.from(new Properties());

        assertTrue(config.inbound().enabled());
        assertEquals(2525, config.inbound().startTlsPort());
        assertEquals(2465, config.inbound().implicitTlsPort());
        assertEquals(2526, config.outbound().port());
        assertEquals(2530, config.postfix().port());
        assertEquals(52_428_800, config.maxMessageSizeBytes());
        assertEquals(100, config.maxRecipients());
        assertEquals(2727, config.admin().port());
    }

    @Test
    void parsesRoutes() {
        Properties properties = new Properties();
        properties.setProperty("edge.outbound.routes", ".example.cn=gm.example.cn:2525,partner.test=IMPLICIT_TLS://relay.test:2465");

        EdgeConfig config = EdgeConfig.from(properties);

        assertEquals(2, config.routes().size());
        assertEquals(".example.cn", config.routes().get(0).domainPattern());
        assertEquals("gm.example.cn", config.routes().get(0).host());
        assertEquals(2525, config.routes().get(0).port());
        assertEquals(EdgeConfig.RouteSecurity.STARTTLS, config.routes().get(0).security());
        assertEquals(EdgeConfig.RouteSecurity.IMPLICIT_TLS, config.routes().get(1).security());
    }

    @Test
    void rejectsStandardTlsProtocolOnGmListener() {
        Properties properties = new Properties();
        properties.setProperty("edge.tls.protocols", "TLSv1.2,TLSv1.3");

        assertThrows(IllegalArgumentException.class, () -> EdgeConfig.from(properties));
    }

    @Test
    void rejectsListenerPortConflict() {
        Properties properties = new Properties();
        properties.setProperty("edge.inbound.starttls-port", "2525");
        properties.setProperty("edge.inbound.implicit-tls-port", "2525");

        assertThrows(IllegalArgumentException.class, () -> EdgeConfig.from(properties));
    }
}
