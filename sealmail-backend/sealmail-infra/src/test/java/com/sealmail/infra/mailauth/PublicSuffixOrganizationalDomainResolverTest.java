package com.sealmail.infra.mailauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicSuffixOrganizationalDomainResolverTest {

    private final PublicSuffixOrganizationalDomainResolver resolver =
            new PublicSuffixOrganizationalDomainResolver();

    @Test
    void resolvesIcannPublicSuffixOrganizationalDomain() {
        assertEquals("example.co.uk", resolver.organizationalDomain("mail.customer.example.co.uk"));
        assertEquals("example.co.uk", resolver.organizationalDomain("example.co.uk"));
    }

    @Test
    void resolvesPrivatePublicSuffixOrganizationalDomain() {
        assertEquals("tenant.github.io", resolver.organizationalDomain("mail.tenant.github.io"));
        assertEquals("foo.blogspot.com", resolver.organizationalDomain("mail.foo.blogspot.com"));
    }

    @Test
    void returnsNullForPublicSuffixOrInvalidDomain() {
        assertNull(resolver.organizationalDomain("co.uk"));
        assertNull(resolver.organizationalDomain("github.io"));
        assertNull(resolver.organizationalDomain("_invalid.example.com"));
    }

    @Test
    void relaxedAlignmentUsesPublicSuffixBoundaries() {
        assertTrue(resolver.relaxedAligned("mail.customer.example.co.uk", "bounce.example.co.uk"));
        assertFalse(resolver.relaxedAligned("mail.customer.example.co.uk", "bounce.attacker.co.uk"));
        assertFalse(resolver.relaxedAligned("mail.tenant.github.io", "bounce.other.github.io"));
    }
}
