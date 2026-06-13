package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigHealthCheckResourceTest {
    private FileConfiguration load(String name) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
            Assertions.assertNotNull(in, name);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testDefaultConfigsPassHealthCheck() throws Exception {
        // Only load the 5 registered config files (old individual files removed)
        Map<String, FileConfiguration> cfgs = new HashMap<>();
        cfgs.put("config", load("config.yml"));
        cfgs.put("bot", load("bot.yml"));
        cfgs.put("guide_book", load("guide_book.yml"));
        cfgs.put("templates", load("templates.yml"));
        cfgs.put("portals", load("portals.yml"));

        List<String> issues = ConfigHealthCheck.validateAll(cfgs::get);
        List<String> fatal = new ArrayList<>();
        for (String s : issues) {
            if (s.startsWith("缺失:")
                    || s.startsWith("非法:")
                    || s.startsWith("类型错误:")
                    || s.startsWith("通知事件缺少模板:")
                    || s.startsWith("模板变量未知:")) {
                fatal.add(s);
            }
        }
        Assertions.assertTrue(fatal.isEmpty(), String.join("\n", fatal));
    }
}
