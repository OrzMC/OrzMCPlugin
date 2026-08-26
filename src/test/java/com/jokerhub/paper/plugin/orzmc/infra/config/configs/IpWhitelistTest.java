package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class IpWhitelistTest {

    @Test
    void fromNull_returnsEmptyListAndFailClose() {
        IpWhitelist config = IpWhitelist.from(null);
        assertTrue(config.allowCountryCode().isEmpty());
        assertFalse(config.failOpen(), "默认应为 fail-close（安全优先）");
    }

    @Test
    void fromEmpty_returnsEmptyListAndFailClose() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        IpWhitelist config = IpWhitelist.from(cfg);
        assertTrue(config.allowCountryCode().isEmpty());
        assertFalse(config.failOpen(), "未配置 fail_open 时应默认 fail-close");
    }

    @Test
    void fromFailOpenTrue_readsConfig() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("fail_open", false)).thenReturn(true);

        IpWhitelist config = IpWhitelist.from(cfg);

        assertTrue(config.failOpen(), "配置 fail_open: true 时应为 fail-open");
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        List<String> list = new ArrayList<>();
        list.add("CN");
        list.add("JP");
        list.add("US");
        when(cfg.get("allow_country_code")).thenReturn(list);

        IpWhitelist config = IpWhitelist.from(cfg);
        assertEquals(List.of("CN", "JP", "US"), config.allowCountryCode());
    }

    @Test
    void from_normalizesCountryCodeToUppercase() {
        // 配置侧归一化为大写 + 去空白：即使管理员写小写/带空格，也不因大小写分叉误拦全服
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        List<String> list = new ArrayList<>();
        list.add("cn");
        list.add(" jp ");
        when(cfg.get("allow_country_code")).thenReturn(list);

        IpWhitelist config = IpWhitelist.from(cfg);
        assertEquals(List.of("CN", "JP"), config.allowCountryCode());
    }

    @Test
    void from_ignoresNullElements() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        List<String> list = new ArrayList<>();
        list.add("CN");
        list.add(null);
        list.add("US");
        when(cfg.get("allow_country_code")).thenReturn(list);

        IpWhitelist config = IpWhitelist.from(cfg);
        assertEquals(List.of("CN", "US"), config.allowCountryCode());
    }
}
