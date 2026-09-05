package com.jokerhub.paper.plugin.orzmc.features.bot;

import com.jokerhub.paper.plugin.orzmc.infra.bot.ImBindings;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImDiscoveryCandidates;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.BuiltinImDriver;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ImGatewayConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.QqPlatformConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthAccessor;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.Locale;
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

    public ImAdminService(
            OrzTextStyles styles, ConfigService configService, HealthAccessor health, BuiltinImDriver builtin) {
        this.styles = styles;
        this.configService = configService;
        this.health = health;
        this.builtin = builtin;
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
            sender.sendMessage(styles.error("写 im_bindings.yml 失败（请检查控制台日志）"));
            return;
        }
        // 绑定成功后清除该会话候选（D11）
        String target = p + ":" + value;
        if (builtin != null && builtin.candidates() != null) {
            builtin.candidates().clear(target);
        }
        sender.sendMessage(styles.success(p + " 会话绑定已写入并持久化：" + role + " = " + value + "（im_bindings.yml；入站/广播即时生效）"));
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
            sender.sendMessage(styles.error("当前 backend 非 builtin（或 QQ 平台未启用/凭据缺失）——无法内建投递测试；"
                    + "请先 im.yml 设 backend=builtin 且配置 platforms.qq 凭据后 /config reload im。"));
            return;
        }
        if (text == null || text.isBlank()) {
            sender.sendMessage(styles.error("测试文本不能为空"));
            return;
        }
        String p = platform.trim().toLowerCase(Locale.ROOT);
        String value = sessionValue(chatType, chatId);
        String target = value.startsWith(p + ":") ? value : p + ":" + value;
        boolean delivered = builtin.sendTo(target, text.trim());
        if (!delivered) {
            sender.sendMessage(styles.error("无可用平台投递 " + target + "（检查 im.yml QQ 平台配置与连接健康）"));
            return;
        }
        sender.sendMessage(styles.info("已向 " + target + " 投递测试消息（尽力一次；失败见健康 builtin." + p + "）"));
    }

    // =====================================================================
    // 内容构造（status/setup 供单测断言）
    // =====================================================================

    /** status 输出行（含健康/绑定/候选）。 */
    public List<String> statusLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        ImGatewayConfig im = ImGatewayConfig.from(configService.getConfig("im"));
        lines.add("=== IM 通道状态 ===");
        lines.add("backend: " + im.backend() + (im.isBuiltin() && builtin == null ? "（builtin 但无可用平台，群功能停用 D3）" : ""));
        if (im.isBuiltin()) {
            QqPlatformConfig qq = readQq();
            lines.add("QQ 平台: " + (qq.usable() ? "启用（凭据齐备）" : "未启用或凭据缺失（im.yml platforms.qq）"));
            var e = health.get("builtin.qq");
            lines.add("  connection: " + (e.wsConnected() ? "已连接" : "未连接")
                    + " | enabled: " + e.enabled()
                    + (e.lastError() == null || e.lastError().isEmpty() ? "" : " | lastError: " + e.lastError()));
        } else {
            var e = health.get("easybot");
            lines.add("EasyBot 网关: " + (e.wsConnected() ? "已连接" : "未连接")
                    + (e.lastError() == null || e.lastError().isEmpty() ? "" : " | lastError: " + e.lastError()));
        }
        lines.add("--- 会话绑定（im_bindings.yml）---");
        List<ImConversation> convs =
                ImBindings.from(configService.getConfig("im_bindings")).conversations();
        if (convs.isEmpty()) {
            lines.add("（未绑定任何会话——QQ 群/私聊来消息后可用 /config im status 查看候选，或手动 bind）");
        }
        for (ImConversation c : convs) {
            lines.add("  " + describe(c));
        }
        lines.add("--- 未绑定候选（D11，绑定后自动清除；复制对应 bind 命令执行即完成）---");
        List<ImDiscoveryCandidates.Candidate> candidates = builtin == null || builtin.candidates() == null
                ? List.of()
                : builtin.candidates().snapshot();
        if (candidates.isEmpty()) {
            lines.add("（无）");
        }
        for (ImDiscoveryCandidates.Candidate c : candidates) {
            lines.add("  " + c.target());
            List<String> cmds = ImDiscoveryCandidates.bindCommands(c.target());
            if (cmds.isEmpty()) {
                continue;
            }
            lines.add("    admin_group=管理群（群主/管理员发管理指令）| player_group=玩家群（公开通知，可略）| admin_dm=管理员私聊");
            for (String cmd : cmds) {
                lines.add("    " + cmd);
            }
        }
        return lines;
    }

    /** setup 首次接入引导（checklist）。 */
    public List<String> firstTimeChecklist() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        ImGatewayConfig im = ImGatewayConfig.from(configService.getConfig("im"));
        QqPlatformConfig qq = readQq();
        lines.add("=== IM 首次接入引导 ===");
        lines.add("1. im.yml backend: " + im.backend() + "（builtin = 插件内置直连；easybot = 外部网关，默认兜底）");
        lines.add(
                "2. QQ 开放平台注册机器人并过审（https://q.qq.com/）后，在 im.yml platforms.qq 填 app_id / client_secret 并 enabled: true");
        lines.add("   QQ 平台当前: " + (qq.usable() ? "凭据齐备" : "未配置或凭据缺失") + "（改完 /config reload im）");
        lines.add("3. 绑定会话（仅控制台/游戏内 op）：/config im bind qq group <群openid> admin_group");
        lines.add("4. 验证下行：/config im test qq group <群openid> 你好；验证上行：群里 @机器人 发消息");
        return lines;
    }

    /** 单条会话绑定描述。 */
    static String describe(ImConversation c) {
        String enabled = c.enabled() ? "" : "（未启用）";
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

    /** 参数校验错误信息；null = 通过。 */
    static String bindError(String platform, String chatType, String chatId, String role) {
        if (platform == null || platform.isBlank()) {
            return "platform 不能为空";
        }
        String p = platform.trim().toLowerCase(Locale.ROOT);
        if (p.length() > 32 || !p.matches("[a-z0-9_]+")) {
            return "platform 非法（仅小写字母/数字/下划线，如 qq）";
        }
        if (chatType == null || !CHAT_TYPES.contains(chatType.trim().toLowerCase(Locale.ROOT))) {
            return "chat_type 仅支持 group|user";
        }
        if (chatId == null || chatId.isBlank()) {
            return "chat_id 不能为空";
        }
        if (role != null && !ROLES.contains(role)) {
            return "role 仅支持 admin_group|player_group|admin_dm";
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
        sender.sendMessage(styles.error("仅控制台/游戏内 op 可执行（D10）"));
        return false;
    }

    private void sendLines(CommandSender sender, List<String> lines) {
        for (String line : lines) {
            sender.sendMessage(Component.text(line));
        }
    }
}
