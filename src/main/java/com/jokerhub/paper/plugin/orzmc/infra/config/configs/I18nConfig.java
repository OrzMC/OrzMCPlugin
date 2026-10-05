package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import com.jokerhub.paper.plugin.orzmc.infra.i18n.Lang;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * i18n 配置（config.yml {@code i18n:} 段，snake_case）。
 *
 * <p>语言决议参数：服务器默认语言（{@code default_lang}）、群平台语言覆盖（{@code platform_langs}，
 * key 为 IM 平台 id：qq/discord/telegram/feishu）、可选语言别名（{@code aliases}，
 * 把未安装语言码指向已安装语言，如 {@code zh-TW: zh-CN}）。键名归属与命名规范见
 * docs/dev/config-schema-governance.md §3.6。</p>
 *
 * @param defaultLang  服务器默认语言码（决议兜底；未安装回落 zh-CN）
 * @param platformLangs 平台 id → 语言码（Bot 交互回复按来源平台决议）
 * @param aliases      语言码别名映射（原始码 → 目标码，均归一化后匹配）
 */
public record I18nConfig(String defaultLang, Map<String, String> platformLangs, Map<String, String> aliases) {

    /** 缺省配置：默认 zh-CN、无平台覆盖、无别名。 */
    public static final I18nConfig DEFAULT = new I18nConfig("zh-CN", Map.of(), Map.of());

    public I18nConfig {
        platformLangs = normalizePlatformMap(platformLangs);
        aliases = normalizeLangMap(aliases);
    }

    /** 从 {@code i18n} 段读取；段缺失 → {@link #DEFAULT}。 */
    public static I18nConfig from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }
        String defaultLang = section.getString("default_lang", "zh-CN");
        Map<String, String> platforms = readStringMap(section.getConfigurationSection("platform_langs"));
        Map<String, String> aliases = readStringMap(section.getConfigurationSection("aliases"));
        return new I18nConfig(defaultLang, platforms, aliases);
    }

    /** 平台覆盖语言；未配置返回 {@code null}。 */
    public String platformLang(String platformId) {
        if (platformId == null) {
            return null;
        }
        return platformLangs.get(platformId.trim().toLowerCase(Locale.ROOT));
    }

    /** 语言别名目标码；无别名返回 {@code null}。 */
    public String aliasFor(String langCode) {
        if (langCode == null) {
            return null;
        }
        return aliases.get(Lang.normalize(langCode));
    }

    private static Map<String, String> readStringMap(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                out.put(key, value);
            }
        }
        return out;
    }

    /** 平台 id 键：小写；值：语言码归一化。 */
    private static Map<String, String> normalizePlatformMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : source.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String key = e.getKey().trim().toLowerCase(Locale.ROOT);
            String value = Lang.normalize(e.getValue());
            if (value != null) {
                out.put(key, value);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** 语言别名：键与值均按语言码规则归一化（zh-TW → zh-TW，区域大写）。 */
    private static Map<String, String> normalizeLangMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : source.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String key = Lang.normalize(e.getKey());
            String value = Lang.normalize(e.getValue());
            if (key != null && value != null) {
                out.put(key, value);
            }
        }
        return Collections.unmodifiableMap(out);
    }
}
