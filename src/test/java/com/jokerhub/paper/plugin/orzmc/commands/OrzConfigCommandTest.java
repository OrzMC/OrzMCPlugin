package com.jokerhub.paper.plugin.orzmc.commands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.lang.reflect.Method;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrzConfigCommandTest {

    private ConfigService configService;
    private OrzTextStyles textStyles;
    private FileConfiguration configConfig;
    private CommandSender sender;
    private Command command;
    private OrzConfigCommand cmd;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        textStyles = mock(OrzTextStyles.class);
        configConfig = mock(FileConfiguration.class);
        sender = mock(CommandSender.class);
        command = mock(Command.class);

        when(configService.getConfig("config")).thenReturn(configConfig);
        when(textStyles.error(anyString())).thenReturn(Component.text("error"));
        when(textStyles.info(anyString())).thenReturn(Component.text("info"));
        when(textStyles.success(anyString())).thenReturn(Component.text("success"));
        when(textStyles.colorSuccess()).thenReturn(TextColor.fromCSSHexString("#00FF00"));
        when(textStyles.colorInfo()).thenReturn(TextColor.fromCSSHexString("#55AAFF"));
        when(textStyles.colorWarn()).thenReturn(TextColor.fromCSSHexString("#FFAA00"));

        cmd = new OrzConfigCommand(configService, textStyles);
    }

    // ---------------------------------------------------------------
    // Subcommand routing
    // ---------------------------------------------------------------

    @Test
    void onCommand_noArgs_sendsUsage() {
        cmd.onCommand(sender, command, "orzmc", new String[] {});
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void onCommand_unknownSubcommand_sendsUsage() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"unknown"});
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void onCommand_returnsTrue() {
        assertTrue(cmd.onCommand(sender, command, "orzmc", new String[] {"list"}));
    }

    // ---------------------------------------------------------------
    // list
    // ---------------------------------------------------------------

    @Test
    void list_listsRegisteredPaths() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"list"});
        // Should have sent multiple messages (one header + each config path)
        verify(sender, atLeast(3)).sendMessage(any(Component.class));
    }

    // ---------------------------------------------------------------
    // get
    // ---------------------------------------------------------------

    @Test
    void get_noPath_sendsUsage() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"get"});
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
        verify(textStyles).error(contains("用法"));
    }

    @Test
    void get_unknownPath_sendsError() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"get", "nonexistent.path"});
        verify(textStyles).error(contains("未知配置路径"));
    }

    @Test
    void get_validPath_showsCurrentValue() {
        when(configConfig.get("tnt.enable")).thenReturn(true);
        cmd.onCommand(sender, command, "orzmc", new String[] {"get", "tnt.enable"});
        // Should show key, value, type, default, file, description
        verify(sender, atLeast(5)).sendMessage(any(Component.class));
    }

    @Test
    void get_validPath_nullCurrent_showsNull() {
        when(configConfig.get("tnt.enable")).thenReturn(null);
        cmd.onCommand(sender, command, "orzmc", new String[] {"get", "tnt.enable"});
        verify(sender, atLeast(5)).sendMessage(any(Component.class));
    }

    // ---------------------------------------------------------------
    // set
    // ---------------------------------------------------------------

    @Test
    void set_noArgs_sendsUsage() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"set"});
        verify(textStyles).error(contains("用法"));
    }

    @Test
    void set_noValue_sendsUsage() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"set", "tnt.enable"});
        verify(textStyles).error(contains("用法"));
    }

    @Test
    void set_unknownPath_sendsError() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"set", "bad.path", "value"});
        verify(textStyles).error(contains("未知配置路径"));
    }

    @Test
    void set_validBooleanValue_savesAndReloads() {
        when(configService.saveConfig("config")).thenReturn(true);
        when(configService.reloadConfig("config")).thenReturn(true);

        cmd.onCommand(sender, command, "orzmc", new String[] {"set", "tnt.enable", "true"});

        verify(configConfig).set("tnt.enable", true);
        verify(configService).saveConfig("config");
        verify(configService).reloadConfig("config");
        verify(textStyles).success(contains("已设置"));
    }

    @Test
    void set_validIntegerValue_savesAndReloads() {
        when(configService.saveConfig("config")).thenReturn(true);
        when(configService.reloadConfig("config")).thenReturn(true);

        cmd.onCommand(sender, command, "orzmc", new String[] {"set", "tnt.place_cooldown", "10"});

        verify(configConfig).set("tnt.place_cooldown", 10);
        verify(configService).saveConfig("config");
        verify(configService).reloadConfig("config");
        verify(textStyles).success(contains("已设置"));
    }

    @Test
    void set_wrongType_showsTypeHelp() {
        // Boolean parseValue throws IllegalArgumentException for invalid input
        cmd.onCommand(sender, command, "orzmc", new String[] {"set", "tnt.enable", "not_a_boolean"});
        verify(textStyles).error(contains("Boolean 类型需要"));
    }

    @Test
    void set_stringValueWithSpaces_joinsArgs() {
        when(configService.saveConfig("config")).thenReturn(true);
        when(configService.reloadConfig("config")).thenReturn(true);
        when(configConfig.get("maintenance.backup_maintenance_motd")).thenReturn(null);

        cmd.onCommand(sender, command, "orzmc", new String[] {
            "set", "maintenance.backup_maintenance_motd", "Server", "is", "down"
        });

        verify(configConfig).set(eq("maintenance.backup_maintenance_motd"), eq("Server is down"));
        verify(textStyles).success(contains("已设置"));
    }

    @Test
    void set_configNull_sendsError() {
        // Return null config for a different config file
        when(configService.getConfig("bot")).thenReturn(null);

        cmd.onCommand(sender, command, "orzmc", new String[] {"set", "cmd_prompt_char", "$"});

        verify(textStyles).error(contains("配置文件未加载"));
    }

    // ---------------------------------------------------------------
    // reset
    // ---------------------------------------------------------------

    @Test
    void reset_noPath_sendsUsage() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"reset"});
        verify(textStyles).error(contains("用法"));
    }

    @Test
    void reset_unknownPath_sendsError() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"reset", "bad.path"});
        verify(textStyles).error(contains("未知配置路径"));
    }

    @Test
    void reset_validPath_resetsToDefault() {
        when(configService.saveConfig("config")).thenReturn(true);
        when(configService.reloadConfig("config")).thenReturn(true);

        cmd.onCommand(sender, command, "orzmc", new String[] {"reset", "tnt.enable"});

        // Default for tnt.enable is false
        verify(configConfig).set("tnt.enable", false);
        verify(configService).saveConfig("config");
        verify(configService).reloadConfig("config");
        verify(textStyles).success(contains("已恢复默认"));
    }

    // ---------------------------------------------------------------
    // dump
    // ---------------------------------------------------------------

    @Test
    void dump_printsAllPaths() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"dump"});
        verify(sender, atLeast(3)).sendMessage(any(Component.class));
    }

    @Test
    void dump_nullConfig_showsNull() {
        when(configService.getConfig("config")).thenReturn(null);

        cmd.onCommand(sender, command, "orzmc", new String[] {"dump"});
        verify(sender, atLeast(3)).sendMessage(any(Component.class));
    }

    // ---------------------------------------------------------------
    // reload
    // ---------------------------------------------------------------

    @Test
    void reload_allConfigs_reloadsAll() {
        cmd.onCommand(sender, command, "orzmc", new String[] {"reload"});
        verify(configService).reloadAll();
        verify(textStyles).success(contains("所有配置文件已重新加载"));
    }

    @Test
    void reload_specificConfig_callsReloadConfig() {
        when(configService.reloadConfig("bot")).thenReturn(true);

        cmd.onCommand(sender, command, "orzmc", new String[] {"reload", "bot"});

        verify(configService).reloadConfig("bot");
        verify(textStyles).success(contains("bot"));
    }

    @Test
    void reload_specificConfig_notFound_sendsError() {
        when(configService.reloadConfig("nonexistent")).thenReturn(false);

        cmd.onCommand(sender, command, "orzmc", new String[] {"reload", "nonexistent"});

        verify(configService).reloadConfig("nonexistent");
        verify(textStyles).error(contains("不存在"));
    }

    // ---------------------------------------------------------------
    // parseValue
    // ---------------------------------------------------------------

    @Test
    void parseValue_booleanTrue_variants() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertEquals(Boolean.TRUE, parseValue.invoke(null, "true", Boolean.class));
        assertEquals(Boolean.TRUE, parseValue.invoke(null, "yes", Boolean.class));
        assertEquals(Boolean.TRUE, parseValue.invoke(null, "1", Boolean.class));
        assertEquals(Boolean.TRUE, parseValue.invoke(null, "TRUE", Boolean.class));
        assertEquals(Boolean.TRUE, parseValue.invoke(null, "True", Boolean.class));
    }

    @Test
    void parseValue_booleanFalse_variants() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertEquals(Boolean.FALSE, parseValue.invoke(null, "false", Boolean.class));
        assertEquals(Boolean.FALSE, parseValue.invoke(null, "no", Boolean.class));
        assertEquals(Boolean.FALSE, parseValue.invoke(null, "0", Boolean.class));
    }

    @Test
    void parseValue_boolean_primitive() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertEquals(Boolean.TRUE, parseValue.invoke(null, "true", boolean.class));
        assertEquals(Boolean.FALSE, parseValue.invoke(null, "false", boolean.class));
    }

    @Test
    void parseValue_boolean_invalid_throws() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertThrows(IllegalArgumentException.class, () -> {
            try {
                parseValue.invoke(null, "xyz", Boolean.class);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    void parseValue_integer_valid() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertEquals(42, parseValue.invoke(null, "42", Integer.class));
        assertEquals(-5, parseValue.invoke(null, "-5", Integer.class));
    }

    @Test
    void parseValue_integer_primitive() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertEquals(99, parseValue.invoke(null, "99", int.class));
    }

    @Test
    void parseValue_integer_invalid_throws() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertThrows(NumberFormatException.class, () -> {
            try {
                parseValue.invoke(null, "not_a_number", Integer.class);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    void parseValue_long_valid() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertEquals(300L, parseValue.invoke(null, "300", Long.class));
        assertEquals(0L, parseValue.invoke(null, "0", Long.class));
    }

    @Test
    void parseValue_long_primitive() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertEquals(100L, parseValue.invoke(null, "100", long.class));
    }

    @Test
    void parseValue_long_invalid_throws() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertThrows(NumberFormatException.class, () -> {
            try {
                parseValue.invoke(null, "not_long", Long.class);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    void parseValue_double_valid() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertEquals(3.14, parseValue.invoke(null, "3.14", Double.class));
        assertEquals(1.0, parseValue.invoke(null, "1.0", Double.class));
    }

    @Test
    void parseValue_double_invalid_throws() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertThrows(NumberFormatException.class, () -> {
            try {
                parseValue.invoke(null, "not_double", Double.class);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    void parseValue_string_returnsRaw() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertEquals("hello world", parseValue.invoke(null, "hello world", String.class));
        assertEquals("  spaced  ", parseValue.invoke(null, "  spaced  ", String.class));
    }

    @Test
    void parseValue_unsupportedType_throws() throws Exception {
        Method parseValue = OrzConfigCommand.class.getDeclaredMethod("parseValue", String.class, Class.class);
        parseValue.setAccessible(true);

        assertThrows(IllegalArgumentException.class, () -> {
            try {
                parseValue.invoke(null, "test", Float.class);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    // ---------------------------------------------------------------
    // formatValue
    // ---------------------------------------------------------------

    @Test
    void formatValue_null_returnsNullString() throws Exception {
        Method formatValue = OrzConfigCommand.class.getDeclaredMethod("formatValue", Object.class);
        formatValue.setAccessible(true);

        assertEquals("<null>", formatValue.invoke(null, (Object) null));
    }

    @Test
    void formatValue_nonNull_returnsStringValue() throws Exception {
        Method formatValue = OrzConfigCommand.class.getDeclaredMethod("formatValue", Object.class);
        formatValue.setAccessible(true);

        assertEquals("42", formatValue.invoke(null, 42));
        assertEquals("hello", formatValue.invoke(null, "hello"));
        assertEquals("true", formatValue.invoke(null, true));
    }

    // ---------------------------------------------------------------
    // typeDisplay
    // ---------------------------------------------------------------

    @Test
    void typeDisplay_booleanClass() throws Exception {
        Method typeDisplay = OrzConfigCommand.class.getDeclaredMethod("typeDisplay", Class.class);
        typeDisplay.setAccessible(true);

        assertEquals("Boolean", typeDisplay.invoke(null, Boolean.class));
        assertEquals("Boolean", typeDisplay.invoke(null, boolean.class));
    }

    @Test
    void typeDisplay_integerClass() throws Exception {
        Method typeDisplay = OrzConfigCommand.class.getDeclaredMethod("typeDisplay", Class.class);
        typeDisplay.setAccessible(true);

        assertEquals("Integer", typeDisplay.invoke(null, Integer.class));
        assertEquals("Integer", typeDisplay.invoke(null, int.class));
    }

    @Test
    void typeDisplay_longClass() throws Exception {
        Method typeDisplay = OrzConfigCommand.class.getDeclaredMethod("typeDisplay", Class.class);
        typeDisplay.setAccessible(true);

        assertEquals("Long", typeDisplay.invoke(null, Long.class));
        assertEquals("Long", typeDisplay.invoke(null, long.class));
    }

    @Test
    void typeDisplay_doubleClass() throws Exception {
        Method typeDisplay = OrzConfigCommand.class.getDeclaredMethod("typeDisplay", Class.class);
        typeDisplay.setAccessible(true);

        assertEquals("Double", typeDisplay.invoke(null, Double.class));
        assertEquals("Double", typeDisplay.invoke(null, double.class));
    }

    @Test
    void typeDisplay_stringClass() throws Exception {
        Method typeDisplay = OrzConfigCommand.class.getDeclaredMethod("typeDisplay", Class.class);
        typeDisplay.setAccessible(true);

        assertEquals("String", typeDisplay.invoke(null, String.class));
    }

    @Test
    void typeDisplay_unknownClass_returnsSimpleName() throws Exception {
        Method typeDisplay = OrzConfigCommand.class.getDeclaredMethod("typeDisplay", Class.class);
        typeDisplay.setAccessible(true);

        assertEquals("Float", typeDisplay.invoke(null, Float.class));
        assertEquals("Object", typeDisplay.invoke(null, Object.class));
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Test
    void get_configNull_sendsNullDisplay() {
        when(configService.getConfig("config")).thenReturn(null);
        cmd.onCommand(sender, command, "orzmc", new String[] {"get", "tnt.enable"});
        verify(sender, atLeast(5)).sendMessage(any(Component.class));
    }

    @Test
    void set_wrongBooleanValue_showsTypeHelp() {
        // Boolean parseValue throws IllegalArgumentException with type guidance
        cmd.onCommand(sender, command, "orzmc", new String[] {"set", "tnt.enable", "bogus"});
        verify(textStyles).error(contains("Boolean 类型需要"));
    }
}
