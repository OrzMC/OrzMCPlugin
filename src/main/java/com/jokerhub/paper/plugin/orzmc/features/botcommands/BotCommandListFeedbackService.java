package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.player.PlayerDisplayNames;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateRenderer;
import java.util.ArrayList;
import java.util.Map;
import org.bukkit.entity.Player;

public final class BotCommandListFeedbackService {
    private final ServerFacade server;
    private final TypedConfigProvider configs;

    public BotCommandListFeedbackService(ServerFacade server, TypedConfigProvider configs) {
        this.server = server;
        this.configs = configs;
    }

    public record OnlineList(String list, String fallback, String header, String onlineCount, String maxCount) {}

    public record WhitelistHeader(String header, String fallback) {}

    public record CleanupNotice(String removedList, String fallback) {}

    public record WhitelistPage(String fallback, Map<String, String> vars) {}

    public OnlineList buildOnlineList(ArrayList<Player> onlinePlayers, int maxPlayers) {
        String header = String.format("------当前在线(%d/%d)------", onlinePlayers.size(), maxPlayers);
        StringBuilder listBuilder = new StringBuilder();
        for (Player p : onlinePlayers) {
            String name = PlayerDisplayNames.format(p);
            listBuilder.append("\n").append(name);
        }
        String list = listBuilder.toString().trim();
        String fallbackDefault = header + (list.isEmpty() ? "" : "\n" + list);
        OnlineList online = new OnlineList(
                list, fallbackDefault, header, String.valueOf(onlinePlayers.size()), String.valueOf(maxPlayers));
        String template = configs.resolveTemplate("command_players", fallbackDefault);
        String fallback = TemplateRenderer.render(template, onlineVars(online));
        return new OnlineList(list, fallback, header, online.onlineCount(), online.maxCount());
    }

    public Map<String, String> onlineVars(OnlineList online) {
        return Map.of(
                "online_count", online.onlineCount(),
                "max_count", online.maxCount(),
                "online_list", online.list());
    }

    public WhitelistHeader buildWhitelistHeader(int total) {
        String headerFallback = String.format("------当前白名单玩家(%d)------", total);
        String headerTemplate = configs.resolveTemplate("command_whitelist_header", headerFallback);
        String header = TemplateRenderer.render(headerTemplate, whitelistHeaderVars(total));
        return new WhitelistHeader(header, header);
    }

    public Map<String, String> whitelistHeaderVars(int total) {
        return Map.of("count", String.valueOf(total));
    }

    public CleanupNotice buildCleanupNotice(java.util.Set<String> removed) {
        String removedList = String.join(
                "\n", removed.stream().map(name -> "✔︎ " + name).collect(java.util.stream.Collectors.toSet()));
        String removedFallbackDefault = "------白名单清理------\n" + removedList;
        CleanupNotice notice = new CleanupNotice(removedList, removedFallbackDefault);
        String template = configs.resolveTemplate("command_whitelist_cleanup", removedFallbackDefault);
        String fallback = TemplateRenderer.render(template, cleanupVars(notice));
        return new CleanupNotice(removedList, fallback);
    }

    public Map<String, String> cleanupVars(CleanupNotice notice) {
        return Map.of("removed_list", notice.removedList());
    }

    public WhitelistPage buildWhitelistPage(String headerText, int pageIndex, int total, String body) {
        Map<String, String> vars = Map.of(
                "header", headerText, "page", String.valueOf(pageIndex), "total", String.valueOf(total), "body", body);
        String fallbackDefault = headerText + "\n第" + pageIndex + "/" + total + "页\n" + body;
        String template = configs.resolveTemplate("command_whitelist_page", fallbackDefault);
        String fallback = TemplateRenderer.render(template, vars);
        return new WhitelistPage(fallback, vars);
    }

    public ArrayList<Player> currentOnlinePlayers() {
        ArrayList<Player> onlinePlayers = new ArrayList<>();
        Object[] objects = server.server().getOnlinePlayers().toArray();
        for (Object obj : objects) {
            if (obj instanceof Player p) {
                onlinePlayers.add(p);
            }
        }
        return onlinePlayers;
    }
}
