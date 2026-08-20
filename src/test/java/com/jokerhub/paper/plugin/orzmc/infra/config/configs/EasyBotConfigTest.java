package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EasyBotConfigTest {

    @Test
    void platformWithoutEnabledFlagDefaultsToDisabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("platforms.qq.admin_group", "qq:123");

        EasyBotConfig easyBot = EasyBotConfig.from(config);

        assertFalse(easyBot.platforms().get("qq").enabled());
    }

    @Test
    void defaultGatewayPortsMatchDistributedConfig() {
        EasyBotConfig easyBot = EasyBotConfig.from(new YamlConfiguration());

        assertEquals("http://127.0.0.1:8080", easyBot.apiServer());
        assertEquals("ws://127.0.0.1:8080", easyBot.wsServer());
    }

    @Test
    void normalizesGatewayValuesAndPlatformKeys() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("api_server", " http://gateway/ ");
        config.set("ws_server", " ws://gateway/ ");
        config.set("api_key", " secret ");
        config.set("platforms.QQ.enabled", true);
        config.set("platforms.QQ.player_group", " qq:players ");

        EasyBotConfig easyBot = EasyBotConfig.from(config);

        assertEquals("http://gateway", easyBot.apiServer());
        assertEquals("ws://gateway", easyBot.wsServer());
        assertEquals("secret", easyBot.apiKey());
        assertTrue(easyBot.platforms().get("qq").enabled());
        assertEquals("qq:players", easyBot.platforms().get("qq").playerGroup());
        assertThrows(
                UnsupportedOperationException.class, () -> easyBot.platforms().put("qq", null));
    }

    @Test
    void connectionFingerprintChangesForTransportSettingsOnly() {
        EasyBotConfig first = new EasyBotConfig(
                "http://gateway/",
                "ws://gateway/",
                "secret",
                Map.of(),
                3,
                3,
                3,
                10,
                5000,
                60000,
                10,
                20000,
                false,
                60000);
        EasyBotConfig same = new EasyBotConfig(
                "http://gateway",
                "ws://gateway",
                "secret",
                Map.of(),
                3,
                3,
                3,
                10,
                5000,
                60000,
                10,
                20000,
                false,
                60000);
        EasyBotConfig changed = new EasyBotConfig(
                "http://gateway",
                "ws://gateway",
                "new-secret",
                Map.of(),
                3,
                3,
                3,
                10,
                5000,
                60000,
                10,
                20000,
                false,
                60000);

        assertEquals(first.connectionFingerprint(), same.connectionFingerprint());
        assertFalse(first.connectionFingerprint().equals(changed.connectionFingerprint()));
    }

    @Test
    void connectionFingerprint_doesNotContainPlaintextApiKey() {
        EasyBotConfig cfg = new EasyBotConfig(
                "http://gateway",
                "ws://gateway",
                "super-secret-key",
                Map.of(),
                3,
                3,
                3,
                10,
                5000,
                60000,
                10,
                20000,
                false,
                60000);

        assertFalse(cfg.connectionFingerprint().contains("super-secret-key"), "指纹不应携带明文 apiKey");
    }
}
