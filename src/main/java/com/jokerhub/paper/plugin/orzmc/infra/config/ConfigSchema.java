package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置 schema 版本常量与 schema 文件清单。
 *
 * <p>版本标记（{@code config-version}）放在 schema 文件顶层。插件启动加载后若磁盘版本低于内置
 * {@link #LATEST_VERSION}，由 {@link ConfigUpgrader} 自动完成「备份 → 深合并补缺失默认键 →
 * 写回新版本标记」，管理员无需再手工删配置重生成。
 *
 * <p>版本号命名空间：历史版本没有可信标记——「无版本键」的安装（#238 曾删除 config-version）与
 * #238 之前写死的旧值 {@code 2} 都不可信，统一按 legacy 对账处理；只有 {@code >= MIN_TRUSTED_VERSION}
 * 的版本才可信并进入版本链。
 */
public final class ConfigSchema {
    private ConfigSchema() {}

    /** 当前最新 schema 版本（config/templates/easybot 同步发版，共享一个版本号）。 */
    public static final int LATEST_VERSION = 11;

    /** 可信版本下限：磁盘值低于此值（含缺失、旧版 config-version: 2）一律按 legacy 处理。 */
    public static final int MIN_TRUSTED_VERSION = 10;

    /** schema 文件顶层版本键名。 */
    public static final String VERSION_KEY = "config-version";

    /**
     * 迁移机制作用的 schema 文件（config 名 → 资源文件名）。
     *
     * <p>运行时数据文件（portals / access_rules / permission / guide_book）不在此列——它们由
     * 插件运行时读写、不存在「升级补默认」语义，永不自动改结构。
     */
    public static final Map<String, String> SCHEMA_FILES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("config", "config.yml");
        m.put("templates", "templates.yml");
        m.put("easybot", "easybot.yml");
        SCHEMA_FILES = Collections.unmodifiableMap(m);
    }
}
