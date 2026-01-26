package com.jokerhub.paper.plugin.orzmc.events;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzGuideBook;
import com.jokerhub.paper.plugin.orzmc.utils.OrzMessageParser;
import com.jokerhub.paper.plugin.orzmc.utils.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.utils.ThrottledNotifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

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
        return plugin.configManager.getConfig("config").getStringList("allow_country_code");
    }

    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (OrzMessageParser.isBackupRunning) {
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
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAcceptAsync(response -> {
                    OrzMC.debugInfo("Response Code : " + response.toString());
                    if (response.statusCode() == 200) {
                        String result = response.body();
                        JsonObject jsonObject = parseToJsonObject(result);
                        String addressInfo = toPrettyFormat(jsonObject);
                        if (jsonObject.has("country_code")) {
                            String countryCode = String.valueOf(jsonObject.get("country_code").getAsString());
                            if (!allowCountList.contains(countryCode)) {
                                String msg = playerName + "(" + ipAddress + ")" + "\n" + countryCode + "\n" + "IP位置不在服务支持区域" + String.join(",", allowCountList);
                                plugin.sendPublicMessage(msg + "\n" + addressInfo);
                                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, OrzTextStyles.error(msg));
                            } else {
                                OrzMC.debugInfo("allowCountList contains: " + countryCode);
                            }
                        } else {
                            OrzMC.debugInfo("ip info has no field: country_code");
                        }
                    }
                }).exceptionally(e -> {
                    String msg = "IP地址解析服务异常: " + e.toString();
                    OrzMC.logger().warning(msg);
                    plugin.sendPublicMessage(msg);
                    return null;
                });
            } catch (Exception e) {
                String msg = e.toString();
                plugin.getLogger().severe(msg);
                plugin.sendPublicMessage(msg);
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
        if (!ThrottledNotifier.shouldRun(key, 1500L)) {
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
        StringBuilder msgBuilder = new StringBuilder(playerName).append(" ");

        boolean isMinusCurrentPlayer = false;
        switch (state) {
            case JOIN -> msgBuilder.append("上线");
            case QUIT -> {
                isMinusCurrentPlayer = true;
                msgBuilder.append("下线");
            }
            case KICK -> {
                isMinusCurrentPlayer = true;
                msgBuilder.append("被踢");
            }
            default -> {
            }
        }

        if (isMinusCurrentPlayer) {
            onlinePlayerCount -= 1;
        }

        msgBuilder.append("\n");
        String tip = String.format("------当前在线(%d/%d)------", onlinePlayerCount, maxPlayerCount);
        msgBuilder.append(tip);

        for (Player p : onlinePlayers) {
            if (p.getUniqueId() == player.getUniqueId() && isMinusCurrentPlayer) {
                continue;
            }
            String name = OrzMessageParser.playerDisplayName(p);
            msgBuilder.append("\n").append(name);
        }
        plugin.sendPublicMessage(msgBuilder.toString());
        plugin.getLogger().info(msgBuilder.toString());
        if (onlinePlayerCount == 0) {
            plugin.sendPrivateMessage("服务器当前无玩家，可进行服务器维护");
        }
    }

    enum PlayerState {
        JOIN, QUIT, KICK
    }
}
