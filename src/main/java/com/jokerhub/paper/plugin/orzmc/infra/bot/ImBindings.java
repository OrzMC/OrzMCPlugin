package com.jokerhub.paper.plugin.orzmc.infra.bot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * builtin 会话绑定读取（im_bindings.yml {@code sessions}，方案 §4.1 / D9）。
 *
 * <p>运行时数据文件由 /orzmc im bind（S8）维护；S7 先落地只读侧：每个平台一节
 * {@code admin_group / player_group / admin_dm}（值为平台原生会话标识，如 {@code group:<GroupOpenID>}），
 * 归一为带平台前缀的 {@link ImConversation}（与 EasyBot target / 入站门槛同构，切 backend 无需改绑定）。
 * 节存在且任一目标非空 = 会话启用（全空 = 未绑定，fail-closed 拒绝入站）。</p>
 */
public final class ImBindings {

    private final Map<String, ImConversation> byPlatform;

    private ImBindings(Map<String, ImConversation> byPlatform) {
        this.byPlatform = byPlatform;
    }

    public static ImBindings from(ConfigurationSection config) {
        Map<String, ImConversation> map = new LinkedHashMap<>();
        if (config != null) {
            ConfigurationSection sessions = config.getConfigurationSection("sessions");
            if (sessions != null) {
                for (String platform : sessions.getKeys(false)) {
                    ConfigurationSection sec = sessions.getConfigurationSection(platform);
                    if (sec == null) {
                        continue;
                    }
                    String adminGroup = normalize(platform, sec.getString("admin_group"));
                    String playerGroup = normalize(platform, sec.getString("player_group"));
                    String adminDm = normalize(platform, sec.getString("admin_dm"));
                    boolean enabled = notBlank(adminGroup) || notBlank(playerGroup) || notBlank(adminDm);
                    map.put(platform, new ImConversation(enabled, adminGroup, playerGroup, adminDm));
                }
            }
        }
        return new ImBindings(map);
    }

    /** 指定平台会话；未绑定（缺节/全空）→ 未启用空会话（入站门槛拒绝，D11）。 */
    public ImConversation conversation(String platform) {
        return byPlatform.getOrDefault(platform, new ImConversation(false, "", "", ""));
    }

    /** 全部已绑定会话（顺序与文件一致），供 ImMessageRouter 出站解析。 */
    public List<ImConversation> conversations() {
        return new ArrayList<>(byPlatform.values());
    }

    /** 值归一为平台前缀 target：已带前缀原样返回；空 → ""；否则补前缀。 */
    private static String normalize(String platform, String value) {
        if (isBlank(value)) {
            return "";
        }
        String v = value.trim();
        return v.startsWith(platform + ":") ? v : platform + ":" + v;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
