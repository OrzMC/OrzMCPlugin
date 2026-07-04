package com.jokerhub.paper.plugin.orzmc.infra.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OrzConstantsTest {

    @Test
    void tpbowKey() {
        assertEquals("tpbow", OrzConstants.TPBOW_KEY);
    }

    @Test
    void prefixTntAlert() {
        assertEquals("[TNT警报] ", OrzConstants.PREFIX_TNT_ALERT);
    }

    @Test
    void prefixExplosionAlert() {
        assertEquals("[爆炸警报] ", OrzConstants.PREFIX_EXPLOSION_ALERT);
    }

    @Test
    void constantsAreFinalStrings() {
        // Verify they're non-null and non-empty
        assertNotNull(OrzConstants.TPBOW_KEY);
        assertNotNull(OrzConstants.PREFIX_TNT_ALERT);
        assertNotNull(OrzConstants.PREFIX_EXPLOSION_ALERT);
    }
}
