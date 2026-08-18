package com.jokerhub.paper.plugin.orzmc.features.server;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * 启动安全自检报告（安全加固 P1-2）。
 *
 * <p>把文章 26 的上线 check-list 自动化，启动即体检：{@link ServerLoadEvent} 时采集在线模式、
 * 命令方块、RCON、白名单/强制、OP 列表与关键防护插件（LuckPerms / LoginSecurity / Grim /
 * Vulcan）安装情况，渲染 {@code security_audit} 模板并 PRIVATE 私信管理员。</p>
 *
 * <p>{@code enable-command-block} 与 RCON 配置不在 Bukkit {@link Server} API 中（无 getter），
 * 从服务端根目录的 {@code server.properties} 读取。</p>
 */
public final class StartupSecurityAuditService {

    /** 文章 26 §3 推荐安装的关键防护插件。 */
    private static final String[] SECURITY_PLUGINS = {"LuckPerms", "LoginSecurity", "Grim", "Vulcan"};

    /** 模板缺失时的 Java 兜底正文（与 templates.yml 的 security_audit 一致）。 */
    private static final String DEFAULT_BODY = "🛡 安全自检报告\n"
            + "在线模式: {online_mode}\n"
            + "命令方块: {command_block}\n"
            + "RCON: {rcon}\n"
            + "白名单: {whitelist}\n"
            + "OP: {ops}\n"
            + "关键插件: {plugins}";

    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final Notifier notifier;
    private final File propertiesFile;

    public StartupSecurityAuditService(ServerFacade server, TypedConfigProvider configs, Notifier notifier) {
        this(server, configs, notifier, new File("server.properties"));
    }

    /** 测试用：注入自定义 server.properties 路径。 */
    StartupSecurityAuditService(
            ServerFacade server, TypedConfigProvider configs, Notifier notifier, File propertiesFile) {
        this.server = server;
        this.configs = configs;
        this.notifier = notifier;
        this.propertiesFile = propertiesFile;
    }

    /** 采集安全配置并 PRIVATE 私信管理员。 */
    public void run(ServerLoadEvent event) {
        Server bukkit = server.server();
        Properties props = serverProperties();
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("online_mode", bukkit.getOnlineMode() ? "正版验证开启" : "离线（高危）");
        vars.put("command_block", isCommandBlockEnabled(props) ? "启用（高危）" : "禁用");
        vars.put("rcon", rconDescription(props));
        vars.put("whitelist", whitelistDescription(bukkit));
        vars.put("ops", opsDescription(bukkit));
        vars.put("plugins", pluginsDescription(bukkit));
        MessageEnvelope env = configs.renderTemplate(TemplateKeys.SECURITY_AUDIT, vars, DEFAULT_BODY);
        notifier.event(TemplateKeys.SECURITY_AUDIT, env);
    }

    private static boolean isCommandBlockEnabled(Properties props) {
        return Boolean.parseBoolean(props.getProperty("enable-command-block", "false"));
    }

    private static String rconDescription(Properties props) {
        if (!Boolean.parseBoolean(props.getProperty("enable-rcon", "false"))) {
            return "未启用";
        }
        return "启用（端口: " + props.getProperty("rcon.port", "25575") + "）";
    }

    private static String whitelistDescription(Server bukkit) {
        if (!bukkit.hasWhitelist()) {
            return "关闭";
        }
        return bukkit.isWhitelistEnforced() ? "开启（强制）" : "开启（非强制）";
    }

    private static String opsDescription(Server bukkit) {
        Set<OfflinePlayer> ops = bukkit.getOperators();
        if (ops == null || ops.isEmpty()) {
            return "0 个";
        }
        List<String> names = ops.stream()
                .map(OfflinePlayer::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted()
                .toList();
        if (names.isEmpty()) {
            return ops.size() + " 个";
        }
        return names.size() + " 个: " + String.join(", ", names);
    }

    private static String pluginsDescription(Server bukkit) {
        List<String> installed = new ArrayList<>();
        for (String name : SECURITY_PLUGINS) {
            Plugin plugin = bukkit.getPluginManager().getPlugin(name);
            if (plugin != null) {
                installed.add(name);
            }
        }
        if (installed.isEmpty()) {
            return "均未安装（高危）";
        }
        return String.join("、", installed);
    }

    private Properties serverProperties() {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(propertiesFile)) {
            props.load(in);
        } catch (IOException e) {
            server.logger().warning("读取 server.properties 失败（按默认值自检）: " + e.getMessage());
        }
        return props;
    }
}
