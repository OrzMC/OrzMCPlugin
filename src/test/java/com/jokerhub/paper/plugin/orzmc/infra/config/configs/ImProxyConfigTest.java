package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.Proxy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ImProxyConfigTest {

    @Test
    void missingSection_isDirect() {
        ImProxyConfig cfg = ImProxyConfig.from(null);
        assertFalse(cfg.effective());
        assertSame(Proxy.NO_PROXY, cfg.toProxy());
        assertNull(cfg.toProxySelector());
    }

    @Test
    void disabledByDefault_whenKeysMissing() {
        ImProxyConfig cfg = ImProxyConfig.from(new YamlConfiguration());
        assertFalse(cfg.effective());
    }

    @Test
    void enabledButNoHostPort_notEffective() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        assertFalse(ImProxyConfig.from(yaml).effective());
    }

    @Test
    void enabledWithHostPort_effective() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("host", "127.0.0.1");
        yaml.set("port", 7890);
        ImProxyConfig cfg = ImProxyConfig.from(yaml);
        assertTrue(cfg.effective());
        Proxy proxy = cfg.toProxy();
        assertNotNull(proxy);
        assertEquals(Proxy.Type.HTTP, proxy.type());
        InetSocketAddress addr = (InetSocketAddress) proxy.address();
        assertEquals("127.0.0.1", addr.getHostString());
        assertEquals(7890, addr.getPort());
        assertNotNull(cfg.toProxySelector());
    }

    @Test
    void platformSectionOverridesGlobal() {
        YamlConfiguration global = new YamlConfiguration();
        global.set("enabled", true);
        global.set("host", "global.proxy");
        global.set("port", 1);
        YamlConfiguration platform = new YamlConfiguration();
        platform.set("enabled", true);
        platform.set("host", "plat.proxy");
        platform.set("port", 2);
        ImProxyConfig resolved = ImProxyConfig.resolve(global, platform);
        assertEquals("plat.proxy", resolved.host());
        assertEquals(2, resolved.port());
    }

    @Test
    void missingPlatformSection_fallsBackToGlobal() {
        YamlConfiguration global = new YamlConfiguration();
        global.set("enabled", true);
        global.set("host", "global.proxy");
        global.set("port", 7890);
        ImProxyConfig resolved = ImProxyConfig.resolve(global, null);
        assertEquals("global.proxy", resolved.host());
        assertTrue(resolved.effective());
    }

    @Test
    void platformDisabledButPresent_stillWinsOverGlobal() {
        YamlConfiguration global = new YamlConfiguration();
        global.set("enabled", true);
        global.set("host", "global.proxy");
        global.set("port", 7890);
        // 平台段存在但 enabled=false → 平台段优先 = 直连（不回退全局——显式平台覆盖）
        ConfigurationSection platform = new YamlConfiguration();
        platform.set("enabled", false);
        ImProxyConfig resolved = ImProxyConfig.resolve(global, platform);
        assertFalse(resolved.effective());
        assertEquals(0, resolved.port());
    }

    @Test
    void disabledGlobal_meansDirect() {
        YamlConfiguration global = new YamlConfiguration();
        global.set("enabled", false);
        global.set("host", "global.proxy");
        global.set("port", 7890);
        assertFalse(ImProxyConfig.from(global).effective());
    }
}
