package com.jokerhub.paper.plugin.orzmc.infra.i18n;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.I18nConfig;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateRenderer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * 多语言服务：语言决议 + 语料读取（内置 bundled ⊕ 数据目录覆盖层 custom）。
 *
 * <p>线程安全：bundled 启动时构建为不可变表；覆盖层经 {@link #reloadCustom()} 重读后
 * 以 {@code volatile} 引用整体替换（{@code tables}），读路径无锁。渲染复用
 * {@code infra.templates.TemplateRenderer} 的 {@code {var}} 引擎（单一实现，不重复造轮子）。</p>
 *
 * <p>决议链：游戏内 = 客户端 locale；Bot 交互回复 = 来源平台 →
 * {@code platform_langs}；广播/默认 = {@code default_lang}。兜底：请求码未安装 →
 * 别名 → 基础码唯一命中 → 默认语言 → zh-CN；zh 也缺 key → 返回 key 本体并按 key 去重告警一次。</p>
 */
public final class I18nService {

    private final ClassLoader resources;
    private final Path dataFolder;
    private final Supplier<I18nConfig> configSupplier;
    private final Logger logger;

    /** bundled 语料（不可变）：code → 语料表。 */
    private final Map<String, MessageTable> bundled;
    /** 生效语料 = bundled ⊕ custom（覆盖层）；reloadCustom() 原子替换。 */
    private volatile Map<String, MessageTable> tables;
    /** 缺失 key 去重告警（lang::key）。 */
    private final Set<String> missingLogged = ConcurrentHashMap.newKeySet();

    public I18nService(ClassLoader resources, Path dataFolder, Supplier<I18nConfig> configSupplier, Logger logger) {
        this.resources = resources;
        this.dataFolder = dataFolder;
        this.configSupplier = configSupplier == null ? () -> I18nConfig.DEFAULT : configSupplier;
        this.logger = logger;
        Map<String, MessageTable> bundled = new LinkedHashMap<>();
        for (String code : I18nLoader.CODES) {
            MessageTable table = I18nLoader.bundled(resources, code);
            if (table == null) {
                warn("内置语言包缺失或损坏: messages/messages_" + code + ".yml");
                table = MessageTable.EMPTY;
            }
            bundled.put(code, table);
        }
        this.bundled = Collections.unmodifiableMap(bundled);
        loadCustomTables();
    }

    // ---------------------------------------------------------------
    // 语言决议
    // ---------------------------------------------------------------

    /** 服务器默认语言（{@code default_lang} → 已安装 → zh-CN）。 */
    public Lang langFor() {
        Lang lang = pickInstalled(config().defaultLang(), 0);
        return lang != null ? lang : Lang.ZH_CN;
    }

    /** 游戏内玩家语言：客户端 locale → 默认。 */
    public Lang langFor(Player player) {
        if (player == null) {
            return langFor();
        }
        Lang lang = Lang.fromLocale(player.locale());
        if (lang == null) {
            return langFor();
        }
        Lang resolved = pickInstalled(lang.code(), 0);
        return resolved != null ? resolved : langFor();
    }

    /** Bot 会话语言：来源平台 → {@code platform_langs} → 默认。 */
    public Lang langFor(String platformId) {
        I18nConfig cfg = config();
        Lang lang = cfg == null ? null : pickInstalled(cfg.platformLang(platformId), 0);
        return lang != null ? lang : langFor();
    }

    /** 已安装语言码（来自内置包，排序）。 */
    public SortedSet<String> installedLangs() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(bundled.keySet()));
    }

    public boolean isInstalled(Lang lang) {
        return lang != null && bundled.containsKey(lang.code());
    }

    // ---------------------------------------------------------------
    // 文案读取
    // ---------------------------------------------------------------

    /** 取文案（无占位符）。缺失 → zh → key 本体；覆盖层空串 = 屏蔽（返回空串）。 */
    public String msg(Lang lang, String key) {
        return msg(lang, key, null);
    }

    /** 取文案并填充 {@code {var}} 占位符。 */
    public String msg(Lang lang, String key, Map<String, String> vars) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        Lang resolved = lang == null ? Lang.ZH_CN : lang;
        String template = lookup(resolved, key);
        if (template == null) {
            warnOnce(resolved, key);
            return key;
        }
        if (template.isEmpty()) {
            return ""; // custom 空串 = 屏蔽
        }
        return TemplateRenderer.render(template, vars);
    }

    /** 该语言语料表（含 custom 覆盖）是否直接持有该 key（不含 zh 回落语义）。 */
    public boolean has(Lang lang, String key) {
        if (lang == null) {
            return false;
        }
        MessageTable table = tables.get(lang.code());
        return table != null && table.has(key);
    }

    private String lookup(Lang lang, String key) {
        MessageTable table = tables.get(lang.code());
        String value = table == null ? null : table.get(key);
        if (value == null && !Lang.ZH_CN.equals(lang)) {
            MessageTable zh = tables.get(Lang.ZH_CN.code());
            value = zh == null ? null : zh.get(key);
        }
        return value;
    }

    // ---------------------------------------------------------------
    // 覆盖层 / 健康
    // ---------------------------------------------------------------

    /** 重读数据目录 {@code messages_custom_<code>.yml} 覆盖层并原子替换生效语料。 */
    public void reloadCustom() {
        loadCustomTables();
    }

    /** 内置语料一致性健康检查（key 集/占位符集/空值），供启动时随 ConfigHealthCheck 风格告警。 */
    public List<String> health() {
        return I18nHealth.check(resources);
    }

    // ---------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------

    private void loadCustomTables() {
        Map<String, MessageTable> next = new LinkedHashMap<>();
        for (Map.Entry<String, MessageTable> e : bundled.entrySet()) {
            try {
                MessageTable custom = I18nLoader.custom(dataFolder, e.getKey());
                next.put(e.getKey(), MessageTable.merge(e.getValue(), custom));
            } catch (Exception ex) {
                warn("读取 i18n 覆盖层失败 (lang=" + e.getKey() + "): " + ex.getMessage() + "，保留上一份");
                return; // 整体回退，保留现状
            }
        }
        tables = Collections.unmodifiableMap(next);
    }

    private I18nConfig config() {
        try {
            I18nConfig cfg = configSupplier.get();
            return cfg == null ? I18nConfig.DEFAULT : cfg;
        } catch (Exception e) {
            return I18nConfig.DEFAULT;
        }
    }

    /** 精确 → 别名 → 基础码唯一命中；均不中返回 {@code null}（由调用方回落默认/zh）。 */
    private Lang pickInstalled(String raw, int depth) {
        if (raw == null || depth > 3) {
            return null;
        }
        String code = Lang.normalize(raw);
        if (code == null) {
            return null;
        }
        if (bundled.containsKey(code)) {
            return new Lang(code);
        }
        String alias = config().aliasFor(code);
        if (alias != null) {
            return pickInstalled(alias, depth + 1);
        }
        if (code.indexOf('-') < 0) {
            // 无区域码（如 "en"）：已安装语言中基础码唯一命中才收敛，多义回落
            String prefix = code + "-";
            Lang hit = null;
            for (String installed : bundled.keySet()) {
                if (installed.startsWith(prefix)) {
                    if (hit != null) {
                        return null;
                    }
                    hit = new Lang(installed);
                }
            }
            return hit;
        }
        return null;
    }

    private void warnOnce(Lang lang, String key) {
        String marker = lang.code() + "::" + key;
        if (missingLogged.add(marker)) {
            warn("i18n 缺失文案 key=" + key + " (lang=" + lang.code() + ")，已返回 key 本体");
        }
    }

    private void warn(String message) {
        if (logger != null) {
            logger.warning("i18n: " + message);
        }
    }
}
