package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class StaticTokenProviderTest {

    @Test
    void currentAndFresh_returnConfiguredToken() {
        StaticTokenProvider p = new StaticTokenProvider("fixed-token");

        assertEquals("fixed-token", p.current());
        assertEquals("fixed-token", p.fresh());
    }

    @Test
    void onAuthFailure_returnsNull_meaningConfigErrorNotSelfHealable() {
        StaticTokenProvider p = new StaticTokenProvider("fixed-token");

        assertNull(p.onAuthFailure());
    }
}
