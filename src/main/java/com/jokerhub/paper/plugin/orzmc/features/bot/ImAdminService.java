package com.jokerhub.paper.plugin.orzmc.features.bot;

import com.jokerhub.paper.plugin.orzmc.infra.bot.ImBindings;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImDiscoveryCandidates;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.BuiltinImDriver;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ImGatewayConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.QqPlatformConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthAccessor;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 * IM 内建网关管理命令逻辑（方案 §4.3 D10-D12；挂在 /config im 管理树下，仅控制台/游戏内 op）。
 *
 * <ul>
 *   <li>{@code setup}：首次接入 checklist（backend/凭据/绑定状态引导）；</li>
 *   <li>{@code status}：连接/Token 健康 + 会话绑定 + 未绑定候选（D11）一览；</li>
 *   <li>{@code bind <platform> <group|user> <chat_id> <admin_group|player_group|admin_dm>}：写 im_bindings.yml
 *       （ConfigService.updateConfig 原子落盘 + 内存即时生效；绑定后清除该会话候选）；</li>
 *   <li>{@code test <platform> <group|user> <chat_id> <text>}：发一条测试文本验证下行可达（D7 尽力一次）。</li>
 * </ul>
 *
 * <p>权限（D10）：本类方法自带控制台放行 / 游戏内 op（orzmc.admin）守卫，命令树层另有 /config 管理根拦截。</p>
 */
public final class ImAdminService {

    /** 会话角色取值（对应 im_bindings sessions.&lt;platform&gt; 键）。 */
    private static final Set<String> ROLES = Set.of("admin_group", "player_group", "admin_dm");
    /** 会话类型取值（聊天对象种类；QQ group=群 / user=私聊）。 */
    private static final Set<String> CHAT_TYPES = Set.of("group", "user");

    private final OrzTextStyles styles;
    private final ConfigService configService;
    private final HealthAccessor health;
    /** backend=builtin 且 QQ 可用时的驱动；easybot/不可用 → null（相关命令给引导而非空转）。 */
    private final BuiltinImDriver builtin;

    private final I18nService i18n;

    public ImAdminService(
            OrzTextStyles styles,
            ConfigService configService,
            HealthAccessor health,
            BuiltinImDriver builtin,
            I18nService i18n) {
        this.styles = styles;
        this.configService = configService;
        this.health = health;
        this.builtin = builtin;
        this.i18n = i18n;
    }

    /** /config im 为运维命令：反馈文案按 default_lang（R1）决议。 */
    private String t(String key) {
        return i18n.msg(i18n.langFor(), key);
    }

    private String t(String key, Map<String, String> vars) {
        return i18n.msg(i18n.langFor(), key, vars);
    }

    // =====================================================================
    // 命令入口（命令树层保证 /config 已 admin 拦截；这里再兜底防串用，便于单测权限拒绝）
    // =====================================================================

    public void setup(CommandSender sender) {
        if (!denyIfNotAdmin(sender)) {
            return;
        }
        sendLines(sender, firstTimeChecklist());
    }

    public void status(CommandSender sender) {
        if (!denyIfNotAdmin(sender)) {
            return;
        }
        sendLines(sender, statusLines());
    }

    public void bind(CommandSender sender, String platform, String chatType, String chatId, String role) {
        if (!denyIfNotAdmin(sender)) {
            return;
        }
        String error = bindError(platform, chatType, chatId, role);
        if (error != null) {
            sender.sendMessage(styles.error(error));
            return;
        }
        String p = platform.trim().toLowerCase(Locale.ROOT);
        String value = sessionValue(chatType, chatId);
        boolean ok = configService.updateConfig("im_bindings", cfg -> cfg.set("sessions." + p + "." + role, value));
        if (!ok) {
            sender.sendMessage(styles.error(t(MessageKeys.CMD_CONFIG_IM_BIND_WRITE_FAILED)));
            return;
        }
        // 绑定成功后清除该会话候选（D11）
        String target = p + ":" + value;
        if (builtin != null && builtin.candidates() != null) {
            builtin.candidates().clear(target);
        }
        sender.sendMessage(styles.success(t(
                MessageKeys.CMD_CONFIG_IM_BIND_OK,
                Map.of("platform", p, "role", String.valueOf(role), "value", value))));
    }

    public void test(CommandSender sender, String platform, String chatType, String chatId, String text) {
        if (!denyIfNotAdmin(sender)) {
            return;
        }
        String error = bindError(platform, chatType, chatId, "admin_group"); // 仅校验 platform/chatType/chatId 形态
        if (error != null) {
            sender.sendMessage(styles.error(error));
            return;
        }
        if (builtin == null) {
            sender.sendMessage(styles.error(t(MessageKeys.CMD_CONFIG_IM_BACKEND_NOT_BUILTIN)));
            return;
        }
        if (text == null || text.isBlank()) {
            sender.sendMessage(styles.error(t(MessageKeys.CMD_CONFIG_IM_TEST_EMPTY)));
            return;
        }
        String p = platform.trim().toLowerCase(Locale.ROOT);
        String value = sessionValue(chatType, chatId);
        String target = value.startsWith(p + ":") ? value : p + ":" + value;
        boolean delivered = builtin.sendTo(target, text.trim());
        if (!delivered) {
            sender.sendMessage(styles.error(t(MessageKeys.CMD_CONFIG_IM_NO_PLATFORM, Map.of("target", target))));
            return;
        }
        sender.sendMessage(
                styles.info(t(MessageKeys.CMD_CONFIG_IM_TEST_SENT, Map.of("target", target, "platform", p))));
    }

    // =====================================================================
    // 内容构造（status/setup 供单测断言）
    // =====================================================================

    /** status 输出行（含健康/绑定/候选；面板文案按 default_lang R1 决议，G6b）。 */
    public List<String> statusLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        ImGatewayConfig im = ImGatewayConfig.from(configService.getConfig("im"));
        lines.add(t(MessageKeys.CMD_CONFIG_IM_PANEL_TITLE));
        String backendState =
                (im.isBuiltin() && builtin == null) ? t(MessageKeys.CMD_CONFIG_IM_STATE_BUILTIN_NO_PLATFORM) : "";
        lines.add(t(
                MessageKeys.CMD_CONFIG_IM_PANEL_BACKEND,
                Map.of("backend", String.valueOf(im.backend()), "state", backendState)));
        if (im.isBuiltin()) {
            QqPlatformConfig qq = readQq();
            lines.add(t(
                    MessageKeys.CMD_CONFIG_IM_PANEL_QQ_PLATFORM,
                    Map.of(
                            "state",
                            qq.usable()
                                    ? t(MessageKeys.CMD_CONFIG_IM_STATE_QQ_USABLE)
                                    : t(MessageKeys.CMD_CONFIG_IM_STATE_QQ_MISSING))));
            var e = health.get("builtin.qq");
            lines.add(t(
                            MessageKeys.CMD_CONFIG_IM_PANEL_CONNECTION,
                            Map.of(
                                    "state",
                                    e.wsConnected()
                                            ? t(MessageKeys.CMD_CONFIG_IM_STATE_CONNECTED)
                                            : t(MessageKeys.CMD_CONFIG_IM_STATE_DISCONNECTED),
                                    "enabled",
                                    String.valueOf(e.enabled())))
                    + lastErrorSuffix(e.lastError()));
        } else {
            var e = health.get("easybot");
            lines.add(t(
                            MessageKeys.CMD_CONFIG_IM_PANEL_EASY_BOT,
                            Map.of(
                                    "state",
                                    e.wsConnected()
                                            ? t(MessageKeys.CMD_CONFIG_IM_STATE_CONNECTED)
                                            : t(MessageKeys.CMD_CONFIG_IM_STATE_DISCONNECTED)))
                    + lastErrorSuffix(e.lastError()));
        }
        lines.add(t(MessageKeys.CMD_CONFIG_IM_PANEL_BINDINGS_TITLE));
        List<ImConversation> convs =
                ImBindings.from(configService.getConfig("im_bindings")).conversations();
        if (convs.isEmpty()) {
            lines.add(t(MessageKeys.CMD_CONFIG_IM_PANEL_NO_BINDINGS));
        }
        for (ImConversation c : convs) {
            lines.add("  " + describe(c));
        }
        lines.add(t(MessageKeys.CMD_CONFIG_IM_PANEL_CANDIDATES_TITLE));
        List<ImDiscoveryCandidates.Candidate> candidates = builtin == null || builtin.candidates() == null
                ? List.of()
                : builtin.candidates().snapshot();
        if (candidates.isEmpty()) {
            lines.add(t(MessageKeys.CMD_CONFIG_IM_PANEL_NONE));
        }
        for (ImDiscoveryCandidates.Candidate c : candidates) {
            lines.add("  " + c.target());
            List<String> cmds = ImDiscoveryCandidates.bindCommands(c.target());
            if (cmds.isEmpty()) {
                continue;
            }
            lines.add(t(MessageKeys.CMD_CONFIG_IM_PANEL_ROLE_LEGEND));
            for (String cmd : cmds) {
                lines.add("    " + cmd);
            }
        }
        return lines;
    }

    /** 连接行尾缀：lastError 为空 → ""，否则原样附加（lastError 为平台/服务器原始错误文本，非 UI）。 */
    private static String lastErrorSuffix(String lastError) {
        return lastError == null || lastError.isEmpty() ? "" : " | lastError: " + lastError;
    }

    /** setup 首次接入引导（checklist）。 */
    public List<String> firstTimeChecklist() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        ImGatewayConfig im = ImGatewayConfig.from(configService.getConfig("im"));
        QqPlatformConfig qq = readQq();
        lines.add(t(MessageKeys.CMD_CONFIG_IM_SETUP_TITLE));
        lines.add(t(MessageKeys.CMD_CONFIG_IM_SETUP_1, Map.of("backend", String.valueOf(im.backend()))));
        lines.add(t(MessageKeys.CMD_CONFIG_IM_SETUP_2));
        lines.add(t(
                MessageKeys.CMD_CONFIG_IM_SETUP_3,
                Map.of(
                        "state",
                        qq.usable()
                                ? t(MessageKeys.CMD_CONFIG_IM_STATE_SETUP_READY)
                                : t(MessageKeys.CMD_CONFIG_IM_STATE_SETUP_MISSING))));
        lines.add(t(MessageKeys.CMD_CONFIG_IM_SETUP_4));
        lines.add(t(MessageKeys.CMD_CONFIG_IM_SETUP_5));
        return lines;
    }

    /** 单条会话绑定描述（运维面板行；R1 文案）。 */
    String describe(ImConversation c) {
        String enabled = c.enabled() ? "" : t(MessageKeys.CMD_CONFIG_IM_STATE_DISABLED_SUFFIX);
        return (c.adminGroup() == null || c.adminGroup().isEmpty() ? "-" : c.adminGroup())
                + " | player: " + (c.playerGroup() == null || c.playerGroup().isEmpty() ? "-" : c.playerGroup())
                + " | dm: " + (c.adminDm() == null || c.adminDm().isEmpty() ? "-" : c.adminDm())
                + enabled;
    }

    // =====================================================================
    // 内部
    // =====================================================================

    private QqPlatformConfig readQq() {
        if (configService.getConfig("im") == null) {
            return QqPlatformConfig.DISABLED;
        }
        return QqPlatformConfig.from(configService.getConfig("im").getConfigurationSection("platforms.qq"));
    }

    /** 参数校验错误信息（运维 R1 文案，包可见供测试）；null = 通过。 */
    String bindError(String platform, String chatType, String chatId, String role) {
        if (platform == null || platform.isBlank()) {
            return t(MessageKeys.CMD_CONFIG_IM_ERR_PLATFORM_EMPTY);
        }
        String p = platform.trim().toLowerCase(Locale.ROOT);
        if (p.length() > 32 || !p.matches("[a-z0-9_]+")) {
            return t(MessageKeys.CMD_CONFIG_IM_ERR_PLATFORM_INVALID);
        }
        if (chatType == null || !CHAT_TYPES.contains(chatType.trim().toLowerCase(Locale.ROOT))) {
            return t(MessageKeys.CMD_CONFIG_IM_ERR_CHAT_TYPE);
        }
        if (chatId == null || chatId.isBlank()) {
            return t(MessageKeys.CMD_CONFIG_IM_ERR_CHAT_ID);
        }
        if (role != null && !ROLES.contains(role)) {
            return t(MessageKeys.CMD_CONFIG_IM_ERR_ROLE);
        }
        return null;
    }

    /** 会话存储值：chat_id 已带类型前缀（如 group:G-1）则原样；否则补 chatType:。 */
    static String sessionValue(String chatType, String chatId) {
        String id = chatId.trim();
        if (id.contains(":")) {
            return id;
        }
        return chatType.trim().toLowerCase(Locale.ROOT) + ":" + id;
    }

    /** 权限守卫：控制台放行；玩家需 op/orzmc.admin；非管理返回错误并拒绝。 */
    private boolean denyIfNotAdmin(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        if (sender instanceof Player player && (player.isOp() || player.hasPermission("orzmc.admin"))) {
            return true;
        }
        sender.sendMessage(styles.error(t(MessageKeys.CMD_CONSOLE_OP_ONLY)));
        return false;
    }

    private void sendLines(CommandSender sender, List<String> lines) {
        for (String line : lines) {
            sender.sendMessage(Component.text(line));
        }
    }
}
