package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 配置 schema 自动升级器（DiscordSRV 式版本门控 + EssentialsX/Bukkit 配方默认合并）。
 *
 * <p>对单个 schema 文件执行：读取磁盘 {@code config-version} →
 * <ul>
 *   <li>{@code > LATEST_VERSION}：插件降级，跳过不逆向迁移；</li>
 *   <li>{@code == LATEST_VERSION}：最新，直接跳过（不写文件、不产生任何告警噪声）；</li>
 *   <li>否则（无版本 / 旧版 2 / 介于可信区间）：备份 {@code .bak} → 深合并补缺失默认键 →
 *       写回版本标记，由调用方落盘。</li>
 * </ul>
 *
 * <p>失败安全：磁盘文件疑似 YAML 损坏（解析为空但文件非空）时不迁移不写回，避免把坏文件覆盖成
 * 半迁移状态；备份失败则中止该文件迁移。
 */
public final class ConfigUpgrader {

    public enum Outcome {
        /** 磁盘版本 == 内置最新版本，无需处理。 */
        UP_TO_DATE,
        /** 已完成升级（调用方负责落盘）。 */
        MIGRATED,
        /** 内置默认资源不可用（测试/异常环境），跳过。 */
        NO_DEFAULTS,
        /** 磁盘文件疑似 YAML 损坏（解析为空但文件非空），跳过避免覆盖。 */
        PARSE_FAILED,
        /** 备份失败，中止迁移。 */
        BACKUP_FAILED,
        /** 磁盘版本高于内置（插件降级），不做逆向迁移。 */
        DOWNGRADE_SKIPPED
    }

    private final Logger logger;

    public ConfigUpgrader(Logger logger) {
        this.logger = logger;
    }

    /** 磁盘备份文件名后缀（升级前保留一份，DiscordSRV 同款思路）。 */
    public static final String BACKUP_SUFFIX = ".bak";

    /**
     * 尝试升级单个 schema 文件。{@code cfg} 为已加载的磁盘配置（可变，迁移直接写回其内存实例），
     * {@code file} 为其落盘路径（用于备份与损坏判定），{@code bundledDefaults} 为 jar 内置默认资源流
     * （可空，方法内负责关闭）。
     */
    public Outcome upgrade(FileConfiguration cfg, File file, InputStream bundledDefaults) {
        if (file == null) {
            return Outcome.NO_DEFAULTS;
        }
        if (bundledDefaults == null) {
            logger.fine(() -> "配置升级跳过（无内置默认资源）: " + file.getName());
            return Outcome.NO_DEFAULTS;
        }
        FileConfiguration defaults;
        try (InputStream in = bundledDefaults) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.warning("配置升级跳过（读取内置默认资源失败）: " + file.getName() + " - " + e.getMessage());
            return Outcome.NO_DEFAULTS;
        }
        if (isParseSuspicious(cfg, file)) {
            logger.severe("配置升级跳过（" + file.getName() + " 解析为空但文件非空，疑似 YAML 损坏，避免覆盖，请人工检查）");
            return Outcome.PARSE_FAILED;
        }

        int from = readVersion(cfg);
        if (from > ConfigSchema.LATEST_VERSION) {
            logger.warning("配置升级跳过（" + file.getName() + " config-version=" + from + " 高于插件内置 "
                    + ConfigSchema.LATEST_VERSION + "，插件可能已降级，不做逆向迁移）");
            return Outcome.DOWNGRADE_SKIPPED;
        }
        boolean trusted = from >= ConfigSchema.MIN_TRUSTED_VERSION;
        if (trusted && from == ConfigSchema.LATEST_VERSION) {
            return Outcome.UP_TO_DATE;
        }

        if (!backup(file)) {
            logger.severe("配置升级中止（备份失败）: " + file.getName());
            return Outcome.BACKUP_FAILED;
        }

        // legacy 旧默认翻转须先于深合并：若缺键由 merge 补成新默认后再判，会被误报为「已自定义保留」。
        boolean legacy = from < ConfigSchema.MIN_TRUSTED_VERSION;
        LegacyDefaultFlips.FlipResult flips = legacy
                ? LegacyDefaultFlips.apply(cfg, defaults)
                : new LegacyDefaultFlips.FlipResult(new ArrayList<>(), new ArrayList<>());

        DefaultsMerger.MergeResult merge = DefaultsMerger.mergeMissingKeys(cfg, defaults);
        for (String conflict : merge.conflicts()) {
            logger.warning("配置合并冲突（" + file.getName() + " " + conflict + " 与内置默认结构不一致，已保留磁盘值，请人工检查）");
        }
        cfg.set(ConfigSchema.VERSION_KEY, ConfigSchema.LATEST_VERSION);

        logReport(file, from, merge.addedKeys(), flips);
        return Outcome.MIGRATED;
    }

    /** 读取磁盘版本：缺失 / 非数字一律按 0（legacy）处理。 */
    private static int readVersion(FileConfiguration cfg) {
        if (!cfg.contains(ConfigSchema.VERSION_KEY)) {
            return 0;
        }
        Object raw = cfg.get(ConfigSchema.VERSION_KEY);
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 解析为空但文件非空 → 疑似 YAML 损坏。 */
    private static boolean isParseSuspicious(FileConfiguration cfg, File file) {
        return file.exists() && file.length() > 0 && cfg.getValues(true).isEmpty();
    }

    private boolean backup(File file) {
        if (!file.exists()) {
            return true;
        }
        try {
            File backup = new File(file.getParentFile(), file.getName() + BACKUP_SUFFIX);
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            logger.warning("备份失败: " + file + " - " + e.getMessage());
            return false;
        }
    }

    private void logReport(File file, int from, List<String> addedKeys, LegacyDefaultFlips.FlipResult flips) {
        String fromLabel = from >= ConfigSchema.MIN_TRUSTED_VERSION
                ? String.valueOf(from)
                : from > 0 ? "legacy(config-version=" + from + ")" : "legacy(无版本标记)";
        StringBuilder sb = new StringBuilder()
                .append("配置升级: ")
                .append(file.getName())
                .append(" schema ")
                .append(fromLabel)
                .append(" → ")
                .append(ConfigSchema.LATEST_VERSION);
        if (addedKeys.isEmpty()) {
            sb.append("，无新增默认键");
        } else {
            sb.append("，新增默认键 ").append(addedKeys.size()).append(" 个");
            sb.append("：").append(groupSummary(addedKeys));
        }
        if (!flips.flipped().isEmpty()) {
            sb.append("，旧默认翻转 ").append(flips.flipped().size()).append(" 项：").append(String.join("；", flips.flipped()));
        }
        if (!flips.keptCustom().isEmpty()) {
            sb.append("，保留自定义 ")
                    .append(flips.keptCustom().size())
                    .append(" 项：")
                    .append(String.join("；", flips.keptCustom()));
        }
        if (new File(file.getParentFile(), file.getName() + BACKUP_SUFFIX).exists()) {
            sb.append("（原文件已备份为 ").append(file.getName()).append(BACKUP_SUFFIX).append("）");
        }
        logger.info(sb.toString());
    }

    /** 按顶层分段汇总新增键数量，避免日志刷屏。 */
    private static String groupSummary(List<String> addedKeys) {
        Map<String, Integer> bySection = new LinkedHashMap<>();
        for (String path : addedKeys) {
            String top = path.indexOf('.') >= 0 ? path.substring(0, path.indexOf('.')) : path;
            bySection.merge(top, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        bySection.forEach(
                (section, count) -> sb.append(section).append("+").append(count).append(" "));
        return sb.toString().trim();
    }
}
