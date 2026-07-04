package com.jokerhub.paper.plugin.orzmc.infra.templates;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TemplateServiceTest {

    @Test
    void playerJoin_returnsMessageEnvelope() {
        YamlConfiguration cfg = new YamlConfiguration();
        Templates templates = Templates.from(cfg);
        MessageEnvelope result = TemplateService.renderEvent("player_join", cfg, templates, Map.of("name", "Test"));
        assertNotNull(result);
        assertEquals(MessageEnvelope.TargetType.PUBLIC, result.targetType());
    }

    @Test
    void playerQuit_returnsMessageEnvelope() {
        YamlConfiguration cfg = new YamlConfiguration();
        Templates templates = Templates.from(cfg);
        MessageEnvelope result = TemplateService.renderEvent("player_quit", cfg, templates, Map.of("name", "Test"));
        assertNotNull(result);
    }

    @Test
    void playerKick_returnsMessageEnvelope() {
        YamlConfiguration cfg = new YamlConfiguration();
        Templates templates = Templates.from(cfg);
        MessageEnvelope result = TemplateService.renderEvent("player_kick", cfg, templates, Map.of("name", "Test"));
        assertNotNull(result);
    }

    @Test
    void exceptionAlert_returnsMessageEnvelope() {
        YamlConfiguration cfg = new YamlConfiguration();
        Templates templates = Templates.from(cfg);
        MessageEnvelope result =
                TemplateService.renderEvent("exception_alert", cfg, templates, Map.of("message", "err"));
        assertNotNull(result);
    }

    @Test
    void tntAlert_returnsMessageEnvelope() {
        YamlConfiguration cfg = new YamlConfiguration();
        Templates templates = Templates.from(cfg);
        MessageEnvelope result = TemplateService.renderEvent("tnt_alert", cfg, templates, Map.of("msg", "boom"));
        assertNotNull(result);
    }

    @Test
    void unknownEventKey_returnsEmpty() {
        YamlConfiguration cfg = new YamlConfiguration();
        Templates templates = Templates.from(cfg);
        MessageEnvelope result = TemplateService.renderEvent("unknown_key", cfg, templates, Map.of());
        assertNotNull(result);
        assertTrue(result.message().isEmpty());
    }

    @Test
    void nullEventKey_returnsEmpty() {
        YamlConfiguration cfg = new YamlConfiguration();
        Templates templates = Templates.from(cfg);
        MessageEnvelope result = TemplateService.renderEvent(null, cfg, templates, Map.of());
        assertNotNull(result);
        assertTrue(result.message().isEmpty());
    }

    @Test
    void emptyEventKey_returnsEmpty() {
        YamlConfiguration cfg = new YamlConfiguration();
        Templates templates = Templates.from(cfg);
        MessageEnvelope result = TemplateService.renderEvent("", cfg, templates, Map.of());
        assertNotNull(result);
        assertTrue(result.message().isEmpty());
    }
}
