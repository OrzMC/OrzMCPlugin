package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 默认值翻转表（版本门控）：把「仍等于旧默认」的磁盘值翻成新默认，do-no-harm 不静默覆盖用户值。
 *
 * <p>与 {@link DefaultsMerger} 的分工：merge 只补「缺失」的键；本表处理「键存在但值 == 旧默认」的
 * 默认值变更（标量或列表整体替换）。仅当磁盘值恰等于旧默认（可推断管理员未自定义）才翻到新默认；
 * 已自定义的值一律保留并在升级报告列出「保留自定义」。</p>
 *
 * <p><b>版本门控（v15 起）</b>：每条 {@link FlipSpec} 带 {@code changedInVersion}（该默认值发生变更
 * 时的 schema 版本）。仅当磁盘 {@code fromVersion < changedInVersion} 时才参与判断——这样可信中间
 * 版本（如 v10→v14 老装）在默认值变更后也能自动翻新，而不是只有 legacy（无标记/旧 2）安装受益。
 * {@code changedInVersion = MIN_TRUSTED_VERSION} 的条目 = 框架引入时一次性收编的 legacy 旧默认
 * （等价于旧版 {@code LegacyDefaultFlips} 行为，只对 {@code from < MIN_TRUSTED_VERSION} 生效）。</p>
 *
 * <p>新默认值取 jar 内置默认资源当前值（{@code defaults.get(path)}），避免代码里新旧默认双份漂移。</p>
 */
public final class DefaultFlips {
    private DefaultFlips() {}

    /** 翻转条目：路径 + 旧默认 + 该默认发生变更的 schema 版本（from &lt; changedInVersion 才生效）。 */
    private record FlipSpec(String path, Object oldDefault, int changedInVersion) {}

    /** 翻转结果：已翻转 + 保留的自定义项（含原因，供升级报告）。 */
    public record FlipResult(List<String> flipped, List<String> keptCustom) {
        public FlipResult {
            flipped = List.copyOf(flipped);
            keptCustom = List.copyOf(keptCustom);
        }
    }

    /** 历史默认变更登记表。changedInVersion = 该默认值变更时的 schema 版本；MIN_TRUSTED_VERSION（10）
     *  = 框架引入时一次性收编的 legacy 旧默认（只对 from&lt;10 生效）。 */
    private static final List<FlipSpec> SPECS = List.of(
            // ---- v10 收编：legacy（无标记/旧 2）安装的旧默认 → 新默认 ----
            new FlipSpec("rank_colors.tab_enabled", true, ConfigSchema.MIN_TRUSTED_VERSION),
            new FlipSpec("chat.max_messages_per_minute", 6, ConfigSchema.MIN_TRUSTED_VERSION),
            new FlipSpec("login_rate_limit.max_login_attempts_per_minute", 5, ConfigSchema.MIN_TRUSTED_VERSION),
            new FlipSpec("login_rate_limit.max_concurrent_per_ip", 3, ConfigSchema.MIN_TRUSTED_VERSION),
            new FlipSpec("player_notify.window_ms", 3000L, ConfigSchema.MIN_TRUSTED_VERSION),
            new FlipSpec(
                    "entity_teleport_whitelist",
                    List.of("TAMEABLE", "ENDERMAN", "ARMOR_STAND", "SHULKER"),
                    ConfigSchema.MIN_TRUSTED_VERSION),
            // guard 默认 deny-list 曾含运维生命周期命令（op/deop/publish/seed/reload/plugman/stop），
            // #658ac7e 收敛为 op/publish/seed（该变更早于 v10 框架，归入 v10 收编）
            new FlipSpec(
                    "guard.blocked_commands",
                    List.of("op", "deop", "publish", "seed", "reload", "plugman", "stop"),
                    ConfigSchema.MIN_TRUSTED_VERSION),
            // ---- v15：entity_teleport_whitelist 增补 MINECART（16 → 17 项）----
            new FlipSpec(
                    "entity_teleport_whitelist",
                    List.of(
                            "TAMEABLE",
                            "ENDERMAN",
                            "ARMOR_STAND",
                            "SHULKER",
                            "VILLAGER",
                            "WANDERING_TRADER",
                            "COW",
                            "PIG",
                            "SHEEP",
                            "CHICKEN",
                            "RABBIT",
                            "GOAT",
                            "MOOSHROOM",
                            "AXOLOTL",
                            "BEE",
                            "IRON_GOLEM"),
                    15));

    public static FlipResult apply(FileConfiguration cfg, FileConfiguration defaults, int fromVersion) {
        List<String> flipped = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (FlipSpec spec : SPECS) {
            if (fromVersion >= spec.changedInVersion()) {
                continue; // 磁盘版本不早于该默认变更版本：非本次迁移对象
            }
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
