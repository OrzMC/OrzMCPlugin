package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TemplateKeysTest {

    @Test
    void all_containsAllKnownKeys() {
        assertTrue(TemplateKeys.ALL.length >= 34, "Should have at least 34 template event keys");
    }

    @Test
    void commandKeys_containsCommandTemplates() {
        assertTrue(TemplateKeys.COMMAND_KEYS.length >= 17, "Should have at least 17 command template keys");
    }

    @Test
    void allKeys_inCommandKeysSubset() {
        for (String key : TemplateKeys.COMMAND_KEYS) {
            boolean found = false;
            for (String all : TemplateKeys.ALL) {
                if (all.equals(key)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Command key " + key + " should be in ALL array");
        }
    }

    @Test
    void noDuplicateKeysInAll() {
        for (int i = 0; i < TemplateKeys.ALL.length; i++) {
            for (int j = i + 1; j < TemplateKeys.ALL.length; j++) {
                assertNotEquals(TemplateKeys.ALL[i], TemplateKeys.ALL[j], "Duplicate key: " + TemplateKeys.ALL[i]);
            }
        }
    }

    @Test
    void playerKeys_present() {
        assertEquals("player_join", TemplateKeys.PLAYER_JOIN);
        assertEquals("player_kick", TemplateKeys.PLAYER_KICK);
        assertEquals("player_quit", TemplateKeys.PLAYER_QUIT);
    }

    @Test
    void tntKey_present() {
        assertEquals("tnt_alert", TemplateKeys.TNT_ALERT);
    }

    @Test
    void securityKeys_present() {
        assertEquals("geoip_block", TemplateKeys.GEOIP_BLOCK);
        assertEquals("whitelist_block", TemplateKeys.WHITELIST_BLOCK);
    }
}
