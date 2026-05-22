package com.sealmail.infra.dns;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DnsTxtResolverTest {

    @Test
    void concatenatesSplitDnsTxtCharacterStrings() {
        assertEquals(
                "v=DKIM1; k=rsa; p=abc/def",
                clean("\"v=DKIM1; k=rsa; p=abc\" \"/def\""));
    }

    @Test
    void concatenatesJndiSplitDnsTxtCharacterStrings() {
        assertEquals(
                "v=DKIM1; k=rsa; p=abc/def",
                clean("\"v=DKIM1; k=rsa; p=abc\" /def"));
    }

    @Test
    void leavesUnquotedTxtValuesUnchanged() {
        assertEquals("v=spf1 mx ~all", clean("v=spf1 mx ~all"));
    }

    private String clean(String value) {
        return new DnsTxtResolver().clean(value);
    }
}
