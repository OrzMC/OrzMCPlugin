package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** 钉住 schema 文件与代码版本同步：资源文件必须内置与 {@link ConfigSchema#LATEST_VERSION} 一致的版本标记。 */
class ConfigSchemaResourceTest {

    private static YamlConfiguration loadResource(String name) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
            org.junit.jupiter.api.Assertions.assertNotNull(in, name);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void schemaFiles_allCarryCurrentVersionMarker() throws Exception {
        for (String fileName : ConfigSchema.SCHEMA_FILES.values()) {
            YamlConfiguration cfg = loadResource(fileName);
            assertEquals(
                    ConfigSchema.LATEST_VERSION,
                    cfg.getInt(ConfigSchema.VERSION_KEY),
                    () -> fileName + " 应内置 config-version: " + ConfigSchema.LATEST_VERSION);
        }
    }

    @Test
    void schemaFiles_exactExpectedMapping() {
        assertEquals(
                Map.of("config", "config.yml", "templates", "templates.yml", "easybot", "easybot.yml"),
                ConfigSchema.SCHEMA_FILES);
    }

    @Test
    void runtimeDataFiles_areNotSchemaFiles() {
        // 运行时数据文件（插件自行读写、无「升级补默认」语义）不得被纳入自动迁移
        for (String runtime : Map.of(
                        "portals",
                        "portals.yml",
                        "access_rules",
                        "access_rules.yml",
                        "permission",
                        "permission.yml",
                        "guide_book",
                        "guide_book.yml")
                .keySet()) {
            assertEquals(false, ConfigSchema.SCHEMA_FILES.containsKey(runtime), runtime + " 不应纳入 schema 迁移");
        }
    }
}
