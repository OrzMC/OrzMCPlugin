package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** 锁定 {@link DefaultsMerger} 的 do-no-harm 深合并语义。 */
class DefaultsMergerTest {

    private static YamlConfiguration config() {
        return new YamlConfiguration();
    }

    @Test
    void addsMissingNestedKeys_keepsExistingValues() {
        YamlConfiguration target = config();
        target.set("existing.top", "keep");
        YamlConfiguration defaults = config();
        defaults.set("existing.top", "default");
        defaults.set("existing.new_key", 3);
        defaults.set("new_section.a", true);
        defaults.set("new_section.b", "x");

        DefaultsMerger.MergeResult result = DefaultsMerger.mergeMissingKeys(target, defaults);

        assertEquals("keep", target.getString("existing.top"), "已有值不得被覆盖");
        assertEquals(3, target.getInt("existing.new_key"), "缺失子键应补齐");
        assertTrue(target.getBoolean("new_section.a"), "缺失段应补齐");
        assertEquals("x", target.getString("new_section.b"));
        assertTrue(result.addedKeys().contains("existing.new_key"));
        assertTrue(result.addedKeys().contains("new_section.a"));
        assertFalse(result.addedKeys().contains("existing.top"));
        assertFalse(result.hasConflicts());
    }

    @Test
    void preservesExplicitEmptyValues() {
        YamlConfiguration target = config();
        target.set("chat.enabled", false);
        target.set("tnt.whitelist", List.of());
        YamlConfiguration defaults = config();
        defaults.set("chat.enabled", true);
        defaults.set("tnt.whitelist", List.of("a"));

        DefaultsMerger.MergeResult result = DefaultsMerger.mergeMissingKeys(target, defaults);

        assertEquals(false, target.getBoolean("chat.enabled"), "显式 false 不得被覆盖");
        assertTrue(target.getStringList("tnt.whitelist").isEmpty(), "显式空列表不得被覆盖");
        assertTrue(result.addedKeys().isEmpty());
    }

    @Test
    void reportsConflict_whenSectionPathOccupiedByScalar() {
        YamlConfiguration target = config();
        target.set("chat", "oops"); // 磁盘把 chat 段写成了标量
        YamlConfiguration defaults = config();
        defaults.set("chat.enabled", true);
        defaults.set("chat.message", "x");

        DefaultsMerger.MergeResult result = DefaultsMerger.mergeMissingKeys(target, defaults);

        assertTrue(result.hasConflicts());
        assertTrue(result.conflicts().contains("chat"));
        assertEquals("oops", target.getString("chat"), "结构冲突应保留磁盘值，不覆盖");
    }

    @Test
    void defaultSectionEmptyInDisk_butKeysMissingInside_areAdded() {
        YamlConfiguration target = config();
        target.createSection("chat"); // 空段存在，但缺键
        YamlConfiguration defaults = config();
        defaults.set("chat.enabled", true);

        DefaultsMerger.mergeMissingKeys(target, defaults);

        assertTrue(target.getBoolean("chat.enabled"), "段存在但键缺失也应补齐");
    }
}
