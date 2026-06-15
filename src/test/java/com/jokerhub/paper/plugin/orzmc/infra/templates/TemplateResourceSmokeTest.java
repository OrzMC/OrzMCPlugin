package com.jokerhub.paper.plugin.orzmc.infra.templates;

import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TemplateResourceSmokeTest extends ServiceTestBase {
    private YamlConfiguration load(String name) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
            Assertions.assertNotNull(in, name);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testCommandTemplatesResolve() throws Exception {
        YamlConfiguration cfg = load("templates.yml");
        List<String> keys = List.of(
                "command_players",
                "command_whitelist_header",
                "command_whitelist_cleanup",
                "command_whitelist_page",
                "command_help",
                "command_whitelist_add_result",
                "command_whitelist_remove_result",
                "command_backup",
                "command_optimize",
                "command_optimize_disabled",
                "command_admin_required",
                "command_usage");
        for (String key : keys) {
            String resolved = TemplateRenderer.resolveTemplate(key, cfg, "fallback");
            Assertions.assertFalse(resolved.isEmpty());
        }
    }
}
