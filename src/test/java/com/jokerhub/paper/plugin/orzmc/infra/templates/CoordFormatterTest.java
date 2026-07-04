package com.jokerhub.paper.plugin.orzmc.infra.templates;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoordFormatterTest {

    private Location location;
    private TemplateOptions opt;

    @BeforeEach
    void setUp() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);

        location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        when(location.getBlockX()).thenReturn(100);
        when(location.getBlockY()).thenReturn(64);
        when(location.getBlockZ()).thenReturn(-200);

        opt = mock(TemplateOptions.class);
        when(opt.coordScale()).thenReturn(1.0);
        when(opt.coordPrecision()).thenReturn(2);
        when(opt.coordUnitLabel()).thenReturn("m");
        when(opt.worldAlias()).thenReturn(Map.of());
        when(opt.worldAliasLocalized()).thenReturn(Map.of());
        when(opt.locale()).thenReturn("zh-CN");
    }

    @Test
    void format_withDefaultPrecision() {
        Map<String, String> result = CoordFormatter.format(location, opt);

        assertEquals("主世界", result.get("world"));
        assertEquals("主世界", result.get("world_alias"));
        assertEquals("100", result.get("x"));
        assertEquals("64", result.get("y"));
        assertEquals("-200", result.get("z"));
        assertEquals("100.00", result.get("x_unit"));
        assertEquals("64.00", result.get("y_unit"));
        assertEquals("-200.00", result.get("z_unit"));
        assertEquals("m", result.get("coord_unit"));
    }

    @Test
    void format_withCustomPrecisionScaleAndUnit() {
        when(opt.coordScale()).thenReturn(0.5);
        when(opt.coordPrecision()).thenReturn(0);
        when(opt.coordUnitLabel()).thenReturn("km");

        Map<String, String> result = CoordFormatter.format(location, opt);

        assertEquals("100", result.get("x"));
        assertEquals("64", result.get("y"));
        assertEquals("-200", result.get("z"));
        assertEquals("50", result.get("x_unit"));
        assertEquals("32", result.get("y_unit"));
        assertEquals("-100", result.get("z_unit"));
        assertEquals("km", result.get("coord_unit"));
    }

    @Test
    void format_negativeScale_usesDefaultOne() {
        when(opt.coordScale()).thenReturn(-1.0);

        Map<String, String> result = CoordFormatter.format(location, opt);

        assertEquals("100.00", result.get("x_unit"));
    }
}
