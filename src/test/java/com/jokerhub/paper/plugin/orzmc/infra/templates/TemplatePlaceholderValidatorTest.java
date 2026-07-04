package com.jokerhub.paper.plugin.orzmc.infra.templates;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.Test;

class TemplatePlaceholderValidatorTest {

    @Test
    void nullConfig_returnsError() {
        List<String> issues = TemplatePlaceholderValidator.validate(null);
        assertFalse(issues.isEmpty());
        assertTrue(issues.contains("templates.yml 未加载"));
    }

    @Test
    void validConfig_passes() {
        FileConfiguration cfg = mock(FileConfiguration.class);
        when(cfg.getString(anyString(), anyString())).thenReturn("");
        when(cfg.get(anyString())).thenReturn(null);
        when(cfg.contains(anyString())).thenReturn(false);

        List<String> issues = TemplatePlaceholderValidator.validate(cfg);
        assertTrue(issues.isEmpty());
    }

    @Test
    void invalidPlaceholder_returnsError() {
        FileConfiguration cfg = mock(FileConfiguration.class);
        when(cfg.getString(anyString(), anyString())).thenReturn("");
        when(cfg.getString(eq("templates.player_join"), anyString())).thenReturn("{invalid_var}");
        when(cfg.get(anyString())).thenReturn(null);
        when(cfg.contains(anyString())).thenReturn(false);

        List<String> issues = TemplatePlaceholderValidator.validate(cfg);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.contains("模板变量未知")));
    }
}
