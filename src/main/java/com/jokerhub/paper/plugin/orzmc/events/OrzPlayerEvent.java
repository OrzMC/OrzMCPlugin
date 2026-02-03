package com.jokerhub.paper.plugin.orzmc.events;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzGuideBook;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.ExceptionFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateRenderer;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateResolvers;
import com.jokerhub.paper.plugin.orzmc.utils.OrzMessageParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class OrzPlayerEvent extends OrzBaseListener {

    public OrzPlayerEvent(OrzMC plugin) {
        super(plugin);
    }

    private static JsonObject parseToJsonObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static String toPrettyFormat(JsonObject jsonObject) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(jsonObject);
    }

    private List<String> allowCountryList() {
        return TypedConfigs.IpWhitelist.from(plugin.configManager.getConfig("ip_whitelist"))
                .allowCountryCode();
    }

    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (WorldMaintenanceService.isRunningGlobal()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, OrzTextStyles.warn("服务器地图备份中，请稍后再尝试登录。"));
            return;
        }
        List<String> allowCountList = allowCountryList();
        if (allowCountList.isEmpty() && event.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.ALLOWED)) {
            return;
        }
        String ipAddress = event.getAddress().getHostAddress();
        String playerName = event.getPlayerProfile().getName();
        if (!ipAddress.isEmpty()) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                // use ip parse service: https://www.geojs.io/docs/v1/endpoints/geo/
                String url = "https://get.geojs.io/v1/ip/geo/" + ipAddress + ".json";
                HttpRequest request =
                        HttpRequest.newBuilder().uri(URI.create(url)).build();
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAcceptAsync(response -> {
                            OrzMC.debugInfo("Response Code : " + response.toString());
                            if (response.statusCode() == 200) {
                                String result = response.body();
                                JsonObject jsonObject = parseToJsonObject(result);
                                String addressInfo = toPrettyFormat(jsonObject);
                                if (jsonObject.has("country_code")) {
                                    String countryCode = String.valueOf(
                                            jsonObject.get("country_code").getAsString());
                                    if (!allowCountList.contains(countryCode)) {
                                        java.util.Map<String, String> vars = new java.util.HashMap<>();
                                        vars.put("name", playerName);
                                        vars.put("ip", ipAddress);
                                        vars.put("country_code", countryCode);
                                        vars.put("allow_list", String.join(",", allowCountList));
                                        vars.put("address_info", addressInfo);
                                        String rendered = TemplateRenderer.render(
                                                TypedConfigs.Templates.from(plugin.configManager.getConfig("templates"))
                                                        .geoipBlock(),
                                                vars);
                                        Notifier.event("geoip_block", rendered);
                                        event.disallow(
                                                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                                                OrzTextStyles.error(
                                                        playerName + "(" + ipAddress + ")" + "\n" + countryCode + "\n"
                                                                + "IP位置不在服务支持区域" + String.join(",", allowCountList)));
                                    } else {
                                        OrzMC.debugInfo("allowCountList contains: " + countryCode);
                                    }
                                } else {
                                    OrzMC.debugInfo("ip info has no field: country_code");
                                }
                            }
                        })
                        .exceptionally(e -> {
                            String msgText = "IP地址解析服务异常: " + e.toString();
                            OrzMC.logger().warning(msgText);
                            String rendered = TemplateRenderer.render(
                                    TypedConfigs.Templates.from(plugin.configManager.getConfig("templates"))
                                            .exceptionAlert(),
                                    java.util.Map.of(
                                            "message", msgText, "stack_summary", ExceptionFormatter.summarize(e)));
                            Notifier.event("exception_alert", rendered);
                            return null;
                        });
            } catch (Exception e) {
                String msgText = e.toString();
                plugin.getLogger().severe(msgText);
                String rendered = TemplateRenderer.render(
                        TypedConfigs.Templates.from(plugin.configManager.getConfig("templates"))
                                .exceptionAlert(),
                        java.util.Map.of("message", msgText, "stack_summary", ExceptionFormatter.summarize(e)));
                Notifier.event("exception_alert", rendered);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        OrzGuideBook.giveNewPlayerAGuideBook(event.getPlayer());
        notifyPlayerChatGroupWithMsg(event.getPlayer(), PlayerState.JOIN);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        notifyPlayerChatGroupWithMsg(event.getPlayer(), PlayerState.QUIT);
    }

    @EventHandler
    public void onPlayerKickLeave(PlayerKickEvent event) {
        notifyPlayerChatGroupWithMsg(event.getPlayer(), PlayerState.KICK);
    }

    private void notifyPlayerChatGroupWithMsg(Player player, PlayerState state) {
        String key = "player_event|" + player.getUniqueId() + "|" + state.name();
        if (!ThrottledNotifier.shouldRunDefault(key)) {
            return;
        }
        ArrayList<Player> onlinePlayers = new ArrayList<>();

        Object[] objects = OrzMC.server().getOnlinePlayers().toArray();
        for (Object obj : objects) {
            if (obj instanceof Player p) {
                onlinePlayers.add(p);
            }
        }

        int onlinePlayerCount = onlinePlayers.size();
        int maxPlayerCount = OrzMC.server().getMaxPlayers();

        String playerName = OrzMessageParser.playerDisplayName(player);
        boolean minusCurrent = (state == PlayerState.QUIT || state == PlayerState.KICK);
        int displayOnlineCount = onlinePlayerCount - (minusCurrent ? 1 : 0);

        StringBuilder listBuilder = new StringBuilder();
        for (Player p : onlinePlayers) {
            if (minusCurrent && p.getUniqueId().equals(player.getUniqueId())) continue;
            listBuilder.append(OrzMessageParser.playerDisplayName(p)).append("\n");
        }
        org.bukkit.Location loc = player.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        TypedConfigs.TemplateOptions opt =
                TypedConfigs.TemplateOptions.from(plugin.configManager.getConfig("templates"));
        String worldAlias = TemplateResolvers.worldAlias(
                world, loc.getWorld() != null ? loc.getWorld().getEnvironment().name() : "", opt);
        double scale = opt.coordScale() <= 0 ? 1.0 : opt.coordScale();
        int precision = Math.max(0, plugin.configManager.getConfig("templates").getInt("templates.coord.precision", 2));
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

        TypedConfigs.Templates tpls = TypedConfigs.Templates.from(plugin.configManager.getConfig("templates"));
        String template =
                switch (state) {
                    case JOIN -> tpls.playerJoin();
                    case QUIT -> tpls.playerQuit();
                    case KICK -> tpls.playerKick();
                };
        String rendered = TemplateRenderer.render(template, vars);
        String eventKey =
                switch (state) {
                    case JOIN -> "player_join";
                    case QUIT -> "player_quit";
                    case KICK -> "player_kick";
                };
        Notifier.event(eventKey, rendered);
        plugin.getLogger().info(rendered);
        if (displayOnlineCount == 0) {
            Component motd = OrzMC.server().motd();
            String motdText = PlainTextComponentSerializer.plainText().serialize(motd);
            String message = "当前无玩家，可进行维护" + "\n" + "--------------------" + "\n" + motdText;
            Notifier.event("server_maintenance_hint", message);
        }
    }

    enum PlayerState {
        JOIN,
        QUIT,
        KICK
    }
}
