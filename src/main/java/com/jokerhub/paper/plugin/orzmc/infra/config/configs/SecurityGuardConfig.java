package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 危险命令拦截（安全加固）配置。
 *
 * <p>对应 config.yml 的 {@code guard:} 段，供 {@code CommandGuardService} 使用：
 * 配置高危命令 deny-list、拦截时是否私信管理员、是否记录命令审计。</p>
 */
public record SecurityGuardConfig(
        boolean enabled, List<String> blockedCommands, boolean notifyAdmins, boolean auditEnabled) {

    /** 默认高危命令 deny-list（小写命令名；支持子命令项如 plugman reload）。
     *
     * <p>仅保留非管理员可实际造成提权/信息泄露的命令：{@code op}（提权）、{@code publish}
     * （绕过 online-mode）、{@code seed}（泄露种子）。{@code stop}/{@code reload}/{@code deop}/
     * {@code plugman} 属正常服务器运维生命周期命令，且原生即受 OP 权限限制，不默认拦截——
     * 否则连管理员也无法停服/重载/管理 OP 与插件。</p>
     */
    public static final List<String> DEFAULT_BLOCKED_COMMANDS = List.of("op", "publish", "seed");

    public static SecurityGuardConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new SecurityGuardConfig(true, DEFAULT_BLOCKED_COMMANDS, true, true);
        }
        boolean enabled = cfg.getBoolean("enabled", true);
        boolean notifyAdmins = cfg.getBoolean("notify_admins", true);
        boolean auditEnabled = cfg.getBoolean("audit_enabled", true);
        List<String> blocked = new ArrayList<>();
        Object rawBlocked = cfg.get("blocked_commands");
        if (rawBlocked instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) continue;
                String cmd = String.valueOf(o).trim().toLowerCase();
                if (!cmd.isEmpty()) blocked.add(cmd);
            }
        }
        return new SecurityGuardConfig(enabled, List.copyOf(blocked), notifyAdmins, auditEnabled);
    }
}
