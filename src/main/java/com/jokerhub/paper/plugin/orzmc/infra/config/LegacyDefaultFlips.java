package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * legacy 安装（无可信 {@code config-version}）的旧默认值翻转表。
 *
 * <p>语义（do-no-harm，对应业界 EssentialsX「不静默覆盖用户值」思路）：仅在磁盘值 == 旧默认时
 * 才翻成新默认——此时可认为管理员没改过；已自定义的值一律保留并记录。
 *
 * <p>新默认值取 jar 内置默认资源当前值（{@code defaults.get(path)}），避免代码里新旧默认双份漂移。
 */
public final class LegacyDefaultFlips {
    private LegacyDefaultFlips() {}

    private record FlipSpec(String path, Object oldDefault) {}

    /** 翻转结果：已翻转 + 保留的自定义项（含原因，供升级报告）。 */
    public record FlipResult(List<String> flipped, List<String> keptCustom) {
        public FlipResult {
            flipped = List.copyOf(flipped);
            keptCustom = List.copyOf(keptCustom);
        }
    }

    /** 历史旧默认 → 新默认：条目仅登记「旧默认」，新默认运行时取自内置默认资源。 */
    private static final List<FlipSpec> SPECS = List.of(
            new FlipSpec("rank_colors.tab_enabled", true),
            new FlipSpec("chat.max_messages_per_minute", 6),
            new FlipSpec("login_rate_limit.max_login_attempts_per_minute", 5),
            new FlipSpec("login_rate_limit.max_concurrent_per_ip", 3),
            new FlipSpec("player_notify.window_ms", 3000L),
            new FlipSpec("entity_teleport_whitelist", List.of("TAMEABLE", "ENDERMAN", "ARMOR_STAND", "SHULKER")));

    public static FlipResult apply(FileConfiguration cfg, FileConfiguration defaults) {
        List<String> flipped = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (FlipSpec spec : SPECS) {
            if (!cfg.contains(spec.path())) {
                continue; // 缺键由 DefaultsMerger 补成新默认，不在此处理
            }
            Object current = cfg.get(spec.path());
            if (valueEquals(current, spec.oldDefault())) {
                Object newDefault = defaults.get(spec.path());
                if (newDefault == null) {
                    kept.add(spec.path() + "（内置默认缺失，未翻转）");
                    continue;
                }
                cfg.set(spec.path(), newDefault);
                flipped.add(spec.path() + ": " + display(spec.oldDefault()) + " → " + display(newDefault));
            } else {
                kept.add(spec.path() + "（当前 " + display(current) + " ≠ 旧默认 " + display(spec.oldDefault()) + "，视为已自定义）");
            }
        }
        return new FlipResult(flipped, kept);
    }

    private static boolean valueEquals(Object a, Object b) {
        if (a instanceof Number n && b instanceof Number m) {
            return n.longValue() == m.longValue(); // YAML int 载入 Integer、代码常量可能 Long，按数值比较
        }
        if (a instanceof List<?> l && b instanceof List<?> r) {
            return l.equals(r);
        }
        return Objects.equals(a, b);
    }

    private static String display(Object v) {
        if (v instanceof Collection<?> c) {
            return c.size() + " 项";
        }
        return String.valueOf(v);
    }
}
