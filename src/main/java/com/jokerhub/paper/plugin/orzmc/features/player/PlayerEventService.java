package com.jokerhub.paper.plugin.orzmc.features.player;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.player.PlayerDisplayNames;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.ExceptionFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateResolvers;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class PlayerEventService {
    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final OrzTextStyles styles;
    private final Notifier notifier;
    private final ThrottledNotifier throttledNotifier;

    public PlayerEventService(
            ServerFacade server,
            TypedConfigProvider configs,
            OrzTextStyles styles,
            Notifier notifier,
            ThrottledNotifier throttledNotifier) {
        this.server = server;
        this.configs = configs;
        this.styles = styles;
        this.notifier = notifier;
        this.throttledNotifier = throttledNotifier;
    }

    public enum PlayerState {
        JOIN,
        QUIT,
        KICK
    }

    public void handleGeoIpDecision(
            AsyncPlayerPreLoginEvent event, String playerName, String ipAddress, GeoIpAccessService.Decision decision) {
        if (decision.allowed()) {
            return;
        }
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("name", playerName);
        vars.put("ip", ipAddress);
        vars.put("country_code", decision.countryCode());
        vars.put("allow_list", String.join(",", decision.allowList()));
        vars.put("address_info", decision.rawJson());
        MessageEnvelope envelope = configs.renderEvent("geoip_block", vars);
        notifier.event("geoip_block", envelope);
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                styles.error(playerName + "(" + ipAddress + ")" + "\n" + decision.countryCode() + "\n" + "IP位置不在服务支持区域"
                        + String.join(",", decision.allowList())));
    }

    public void handleGeoIpException(Throwable e) {
        String msgText = "IP地址解析服务异常: " + e.toString();
        server.logger().warning(msgText);
        MessageEnvelope envelope = configs.renderEvent(
                "exception_alert",
                java.util.Map.of("message", msgText, "stack_summary", ExceptionFormatter.summarize(e)));
        notifier.event("exception_alert", envelope);
    }

    public void notifyPlayerState(Player player, PlayerState state) {
        String key = "player_event|" + player.getUniqueId() + "|" + state.name();
        if (!throttledNotifier.shouldRunDefault(key)) {
            return;
        }
        ArrayList<Player> onlinePlayers = new ArrayList<>();
        Object[] objects = server.server().getOnlinePlayers().toArray();
        for (Object obj : objects) {
            if (obj instanceof Player p) {
                onlinePlayers.add(p);
            }
        }
        int onlinePlayerCount = onlinePlayers.size();
        int maxPlayerCount = server.server().getMaxPlayers();
        String playerName = PlayerDisplayNames.format(player);
        boolean minusCurrent = (state == PlayerState.QUIT || state == PlayerState.KICK);
        int displayOnlineCount = onlinePlayerCount - (minusCurrent ? 1 : 0);
        StringBuilder listBuilder = new StringBuilder();
        for (Player p : onlinePlayers) {
            if (minusCurrent && p.getUniqueId().equals(player.getUniqueId())) continue;
            listBuilder.append(PlayerDisplayNames.format(p)).append("\n");
        }
        org.bukkit.Location loc = player.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        TypedConfigs.TemplateOptions opt = configs.templateOptions();
        String worldAlias = TemplateResolvers.worldAlias(
                world, loc.getWorld() != null ? loc.getWorld().getEnvironment().name() : "", opt);
        double scale = opt.coordScale() <= 0 ? 1.0 : opt.coordScale();
        int precision = Math.max(0, opt.coordPrecision());
        String fmt = "%." + precision + "f";
        String xUnit = String.format(fmt, loc.getBlockX() * scale);
        String yUnit = String.format(fmt, loc.getBlockY() * scale);
        String zUnit = String.format(fmt, loc.getBlockZ() * scale);
        boolean isAdmin = (player.isOp() || player.hasPermission("orzmc.admin"));
        String role = isAdmin ? "admin" : "member";
        java.util.Set<String> permKeys = new java.util.HashSet<>();
        for (org.bukkit.permissions.PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (info != null && info.getValue()) {
                String perm = info.getPermission();
                if (!perm.isEmpty()) {
                    permKeys.add(perm);
                }
            }
        }
        String groupAlias = TemplateResolvers.roleGroupAliasFromPermissions(permKeys, opt);
        String roleAlias = groupAlias != null ? groupAlias : TemplateResolvers.roleAlias(isAdmin, opt);
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("name", playerName);
        vars.put("world", world);
        vars.put("world_alias", worldAlias);
        vars.put("x", String.valueOf(loc.getBlockX()));
        vars.put("y", String.valueOf(loc.getBlockY()));
        vars.put("z", String.valueOf(loc.getBlockZ()));
        vars.put("x_unit", xUnit);
        vars.put("y_unit", yUnit);
        vars.put("z_unit", zUnit);
        vars.put("coord_unit", opt.coordUnitLabel());
        vars.put("role", role);
        vars.put("role_alias", roleAlias);
        vars.put("online_count", String.valueOf(displayOnlineCount));
        vars.put("max_count", String.valueOf(maxPlayerCount));
        vars.put("online_list", listBuilder.toString().trim());
        String eventKey =
                switch (state) {
                    case JOIN -> "player_join";
                    case QUIT -> "player_quit";
                    case KICK -> "player_kick";
                };
        MessageEnvelope envelope = configs.renderEvent(eventKey, vars);
        notifier.event(eventKey, envelope);
        server.logger().info(envelope.message());
        if (displayOnlineCount == 0) {
            Component motd = server.server().motd();
            String motdText = PlainTextComponentSerializer.plainText().serialize(motd);
            MessageEnvelope hint = configs.renderEvent("server_maintenance_hint", java.util.Map.of("motd", motdText));
            notifier.event("server_maintenance_hint", hint);
        }
    }
}
