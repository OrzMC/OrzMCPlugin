package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TemplateKeysTest {

    @Test
    void all_containsAllKnownKeys() {
        assertTrue(TemplateKeys.ALL.length >= 43, "Should have at least 43 template event keys");
    }

    @Test
    void all_keysHaveDefaultsInBundledTemplates() {
        // ALL 由 ConfigHealthCheck 全量校验；内置 templates.yml 默认必须覆盖每个 key，
        // 否则全新安装会在启动健康检查处持续告警。
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("templates.yml");
        assertNotNull(in, "classpath templates.yml missing");
        var bundled = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        for (String key : TemplateKeys.ALL) {
            assertTrue(bundled.contains("templates." + key), "内置 templates.yml 缺默认模板: templates." + key);
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
        assertEquals("geoip_unverifiable", TemplateKeys.GEOIP_UNVERIFIABLE);
        assertEquals("whitelist_block", TemplateKeys.WHITELIST_BLOCK);
        assertEquals("command_guard_blocked", TemplateKeys.COMMAND_GUARD_BLOCKED);
        assertEquals("security_audit", TemplateKeys.SECURITY_AUDIT);
        assertEquals("login_rate_limit_alert", TemplateKeys.LOGIN_RATE_LIMIT_ALERT);
        assertEquals("exploit_blocked", TemplateKeys.EXPLOIT_BLOCKED);
        assertEquals("ip_blacklist_block", TemplateKeys.IP_BLACKLIST_BLOCK);
        assertEquals("player_name_block", TemplateKeys.PLAYER_NAME_BLOCK);
    }
}
