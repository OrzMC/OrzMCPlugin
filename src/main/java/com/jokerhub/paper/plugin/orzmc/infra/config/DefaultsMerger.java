package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 把 jar 内置默认树深合并进磁盘配置：只补「缺失」的键，已存在的值一律不覆盖（do-no-harm）。
 *
 * <p>语义钉死（供单测锁定）：
 * <ul>
 *   <li>仅当路径缺失才注入默认值——包括显式空列表、空串、null 值均视为「存在」，不碰；</li>
 *   <li>已存在的值即使是旧默认也不在本层处理（旧默认翻转属显式迁移步职责）；</li>
 *   <li>默认是段、磁盘同名位置却是标量时视为结构冲突：保留磁盘值、不覆盖、记录冲突供上层告警。</li>
 * </ul>
 *
 * <p>纯数据结构变换，不依赖 Bukkit 服务端，可直接单测。
 */
public final class DefaultsMerger {
    private DefaultsMerger() {}

    /** 合并结果：新增键路径 + 结构冲突路径（均不可变）。 */
    public record MergeResult(List<String> addedKeys, List<String> conflicts) {
        public MergeResult {
            addedKeys = List.copyOf(addedKeys);
            conflicts = List.copyOf(conflicts);
        }

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }
    }

    public static MergeResult mergeMissingKeys(ConfigurationSection target, ConfigurationSection defaults) {
        List<String> added = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        mergeInto(target, defaults, null, added, conflicts);
        return new MergeResult(added, conflicts);
    }

    private static void mergeInto(
            ConfigurationSection target,
            ConfigurationSection defaults,
            String prefix,
            List<String> added,
            List<String> conflicts) {
        for (String key : defaults.getKeys(false)) {
            String path = prefix == null ? key : prefix + "." + key;
            Object defaultValue = defaults.get(key);
            if (defaultValue instanceof ConfigurationSection defaultSection) {
                ConfigurationSection existing = target.getConfigurationSection(key);
                if (existing == null) {
                    if (target.isSet(key)) {
                        // 磁盘同名位置是标量/列表，与默认段结构冲突：保留磁盘值，不覆盖
                        conflicts.add(path);
                        continue;
                    }
                    existing = target.createSection(key);
                }
                mergeInto(existing, defaultSection, path, added, conflicts);
            } else if (!target.contains(key)) {
                // 递归已定位到段内：contains/set 用相对键，path 仅用于上报
                target.set(key, defaultValue);
                added.add(path);
            }
        }
    }
}
