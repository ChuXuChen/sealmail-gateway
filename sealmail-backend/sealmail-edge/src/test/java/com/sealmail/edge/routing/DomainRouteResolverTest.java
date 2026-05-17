package com.sealmail.edge.routing;

import com.sealmail.edge.config.EdgeConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainRouteResolverTest {
    @Test
    void resolvesMostSpecificRoute() {
        DomainRouteResolver resolver = new DomainRouteResolver(List.of(
                new EdgeConfig.Route(".example.cn", "suffix", 2525, EdgeConfig.RouteSecurity.STARTTLS),
                new EdgeConfig.Route("partner.example.cn", "exact", 2465, EdgeConfig.RouteSecurity.IMPLICIT_TLS)
        ));

        MailRoute route = resolver.resolve(List.of("<user@partner.example.cn>")).orElseThrow();

        assertEquals("exact", route.host());
        assertEquals(2465, route.port());
        assertEquals(EdgeConfig.RouteSecurity.IMPLICIT_TLS, route.security());
    }

    @Test
    void resolvesSuffixRoute() {
        DomainRouteResolver resolver = new DomainRouteResolver(List.of(
                new EdgeConfig.Route(".example.cn", "suffix", 2525, EdgeConfig.RouteSecurity.STARTTLS)
        ));

        MailRoute route = resolver.resolve(List.of("<user@mail.example.cn>")).orElseThrow();

        assertEquals("suffix", route.host());
    }

    @Test
    void returnsEmptyForUnknownDomain() {
        DomainRouteResolver resolver = new DomainRouteResolver(List.of(
                new EdgeConfig.Route(".example.cn", "suffix", 2525, EdgeConfig.RouteSecurity.STARTTLS)
        ));

        assertTrue(resolver.resolve(List.of("<user@example.com>")).isEmpty());
    }
}
