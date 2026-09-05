package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ImGatewayConfigTest {

    @Test
    void missingSection_defaultsToEasybot() {
        ImGatewayConfig cfg = ImGatewayConfig.from(null);

        assertEquals(ImGatewayConfig.BACKEND_EASY, cfg.backend());
        assertFalse(cfg.isBuiltin());
    }

    @Test
    void emptySection_defaultsToEasybot() {
        ImGatewayConfig cfg = ImGatewayConfig.from(new YamlConfiguration());

        assertEquals(ImGatewayConfig.BACKEND_EASY, cfg.backend());
    }

    @Test
    void builtinValue_isParsed() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("backend", "builtin");

        ImGatewayConfig cfg = ImGatewayConfig.from(yaml);

        assertEquals(ImGatewayConfig.BACKEND_BUILTIN, cfg.backend());
        assertTrue(cfg.isBuiltin());
    }

    @Test
    void builtinValue_caseAndWhitespaceInsensitive() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("backend", "  Builtin  ");

        assertTrue(ImGatewayConfig.from(yaml).isBuiltin());
    }

    @Test
    void unknownValue_fallsBackToEasybot() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("backend", "hybrid");

        ImGatewayConfig cfg = ImGatewayConfig.from(yaml);

        assertEquals(ImGatewayConfig.BACKEND_EASY, cfg.backend());
    }

    @Test
    void explicitEasybotValue_parsed() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("backend", "easybot");

        ImGatewayConfig cfg = ImGatewayConfig.from(yaml);

        assertEquals(ImGatewayConfig.BACKEND_EASY, cfg.backend());
        assertFalse(cfg.isBuiltin());
    }
}
