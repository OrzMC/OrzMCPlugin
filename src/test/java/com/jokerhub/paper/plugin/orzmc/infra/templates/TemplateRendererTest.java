package com.jokerhub.paper.plugin.orzmc.infra.templates;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    @Test
    void render_replacesPlaceholders() {
        String tpl = "{name} 上线 世界:{world} 坐标:{x},{y},{z}";
        String out =
                TemplateRenderer.render(tpl, Map.of("name", "Steve", "world", "world", "x", "1", "y", "64", "z", "1"));
        assertEquals("Steve 上线 世界:world 坐标:1,64,1", out);
    }

    @Test
    void render_nullTemplate_returnsEmpty() {
        assertEquals("", TemplateRenderer.render(null, Map.of()));
    }

    @Test
    void render_emptyTemplate_returnsEmpty() {
        assertEquals("", TemplateRenderer.render("", Map.of()));
    }

    @Test
    void render_nullVars_returnsTemplateAsIs() {
        assertEquals("hello {name}", TemplateRenderer.render("hello {name}", null));
    }

    @Test
    void render_missingVar_leavesPlaceholder() {
        assertEquals("hello {name}", TemplateRenderer.render("hello {name}", Map.of("other", "val")));
    }

    @Test
    void render_nullVarValue_replacesWithEmpty() {
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("name", null);
        assertEquals("hello ", TemplateRenderer.render("hello {name}", vars));
    }

    @Test
    void renderEnvelope_defaultFormat() {
        YamlConfiguration cfg = new YamlConfiguration();
        MessageEnvelope env = TemplateRenderer.renderEnvelope("test_key", "hello {name}", Map.of("name", "world"), cfg);
        assertEquals("hello world", env.message());
        assertEquals(MessageEnvelope.Format.DEFAULT, env.format());
    }

    @Test
    void renderEnvelope_codeBlockFormat() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("templates.format.test_key", "CODE_BLOCK");
        MessageEnvelope env = TemplateRenderer.renderEnvelope("test_key", "hello", Map.of(), cfg);
        assertEquals(MessageEnvelope.Format.CODE_BLOCK, env.format());
    }

    @Test
    void resolveTemplate_usesDirectTemplate() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("templates.command_help", "帮助信息");
        assertEquals("帮助信息", TemplateRenderer.resolveTemplate("command_help", cfg, "fallback"));
    }

    @Test
    void resolveTemplate_fallsBack() {
        assertEquals("fallback", TemplateRenderer.resolveTemplate("missing", new YamlConfiguration(), "fallback"));
    }

    @Test
    void resolveTemplate_nullCfg_returnsFallback() {
        assertEquals("fb", TemplateRenderer.resolveTemplate("key", null, "fb"));
    }
}
