package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SafeKeysTest {

    @Test
    void encodeTargetKey_replacesDotsWithUnderscores() {
        assertEquals("a_b_c", SafeKeys.encodeTargetKey("a.b.c"));
    }

    @Test
    void encodeTargetKey_handlesPlainString() {
        assertEquals("hello", SafeKeys.encodeTargetKey("hello"));
    }

    @Test
    void encodeTargetKey_handlesEmptyString() {
        assertEquals("", SafeKeys.encodeTargetKey(""));
    }

    @Test
    void encodeTargetKey_handlesNull() {
        assertEquals("", SafeKeys.encodeTargetKey(null));
    }

    @Test
    void decodeTargetKey_replacesUnderscoresWithDots() {
        assertEquals("a.b.c", SafeKeys.decodeTargetKey("a_b_c"));
    }

    @Test
    void decodeTargetKey_handlesPlainString() {
        assertEquals("hello", SafeKeys.decodeTargetKey("hello"));
    }

    @Test
    void decodeTargetKey_handlesEmptyString() {
        assertEquals("", SafeKeys.decodeTargetKey(""));
    }

    @Test
    void decodeTargetKey_handlesNull() {
        assertEquals("", SafeKeys.decodeTargetKey(null));
    }

    @Test
    void roundtrip_returnsOriginal() {
        // encode replaces dots with underscores; decode replaces underscores with dots
        String original = "whitelist.force.whitelist";
        assertEquals(original, SafeKeys.decodeTargetKey(SafeKeys.encodeTargetKey(original)));
    }

    @Test
    void roundtrip_withoutDots() {
        String original = "helloworld";
        assertEquals(original, SafeKeys.decodeTargetKey(SafeKeys.encodeTargetKey(original)));
    }

    @Test
    void encodeDecode_areNotIdentity() {
        // Only dots get encoded to underscores
        assertNotEquals("a.b", SafeKeys.encodeTargetKey("a.b"));
        // Only underscores get decoded to dots
        assertNotEquals("a_b", SafeKeys.decodeTargetKey("a_b"));
    }
}
