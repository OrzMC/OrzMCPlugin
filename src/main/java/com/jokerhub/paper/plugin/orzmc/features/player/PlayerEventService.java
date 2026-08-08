package com.jokerhub.paper.plugin.orzmc.features.player;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.CoordFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.templates.ExceptionFormatter;
import java.time.Duration;
import java.util.ArrayList;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class PlayerEventService {
    /** GeoIP 异常告警私信的限频窗口：上游故障时避免每次登录都向管理员发一条 DM。 */
    private static final long GEOIP_ALERT_THROTTLE_MS = Duration.ofMinutes(1).toMillis();

    private static final String GEOIP_ALERT_THROTTLE_KEY = "geoip_exception_alert";

    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final OrzTextStyles styles;
    private final Notifier notifier;
    private final ThrottledNotifier throttledNotifier;
    private final OnlineListFormatter listFormatter;

    public PlayerEventService(
            ServerFacade server,
            TypedConfigProvider configs,
            OrzTextStyles styles,
            Notifier notifier,
            ThrottledNotifier throttledNotifier,
            OnlineListFormatter listFormatter) {
        this.server = server;
        this.configs = configs;
        this.styles = styles;
        this.notifier = notifier;
        this.throttledNotifier = throttledNotifier;
        this.listFormatter = listFormatter;
    }

    public enum PlayerState {
        JOIN,
        QUIT,
        KICK
    }

    public void handleGeoIpDecision(
            AsyncPlayerPreLoginEvent event, String playerName, String ipAddress, GeoIpAccessService.Decision decision) {
        if (decision.allowed()) {
            // fail-open 放行；若因上游查询失败放行，仍私信告警管理员（不入玩家群）
            if (decision.lookupFailed()) {
                handleGeoIpLookupFailure(playerName, ipAddress);
            }
            return;
        }
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("name", playerName);
        vars.put("ip", ipAddress);
        vars.put("country_code", decision.countryCode());
        vars.put("allow_list", String.join(",", decision.allowList()));
        vars.put("address_info", formatAddressInfo(decision.rawJson()));
        MessageEnvelope envelope = configs.renderEvent("geoip_block", vars);
        notifier.event("geoip_block", envelope);
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                styles.error(playerName + "(" + ipAddress + ")" + "\n" + decision.countryCode() + "\n" + "IP位置不在服务支持区域"
                        + String.join(",", decision.allowList())));
    }

    /** 将 GeoIP 返回的原始 JSON 格式化为可读的多行形式；空或非法内容原样返回。 */
    static String formatAddressInfo(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return "";
        }
        try {
            return new com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(com.google.gson.JsonParser.parseString(rawJson));
        } catch (com.google.gson.JsonSyntaxException e) {
            return rawJson;
        }
    }

    public void handleGeoIpException(Throwable e) {
        sendGeoIpAlert("IP地址解析服务异常: " + e.toString(), ExceptionFormatter.summarize(e));
    }

    /**
     * 阻塞等待 GeoIP 决策结果并据此放行/拦截。
     *
     * <p>在异步的 AsyncPlayerPreLoginEvent 处理器内调用：只阻塞当前 netty 线程，
     * 不会阻塞主线程。超时未取到结果或查询异常均 fail-open 放行，但告警到日志与群。</p>
     *
     * @param decisionFuture GeoIP 查询的异步结果
     * @param timeoutMs 阻塞等待上限，超过则按超时处理
     */
    public void handleGeoIpPreLogin(
            AsyncPlayerPreLoginEvent event,
            String playerName,
            String ipAddress,
            java.util.concurrent.CompletableFuture<GeoIpAccessService.Decision> decisionFuture,
            long timeoutMs) {
        GeoIpAccessService.Decision decision;
        try {
            decision = decisionFuture.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            handleGeoIpTimeout(playerName, ipAddress, timeoutMs);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleGeoIpException(e);
            return;
        } catch (Exception e) {
            handleGeoIpException(e);
            return;
        }
        handleGeoIpDecision(event, playerName, ipAddress, decision);
    }

    public void handleGeoIpTimeout(String playerName, String ipAddress, long timeoutMs) {
        sendGeoIpAlert(
                "IP地址解析超时(" + timeoutMs + "ms)，已放行: " + playerName + "(" + ipAddress + ")", "geoip lookup timeout");
    }

    /**
     * GeoIP 上游查询失败（非超时）但 fail-open 放行时调用：告警到日志与管理员私信。
     *
     * <p>经 {@link com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier} 路由为
     * {@code exception_alert}（PRIVATE → 各平台 admin_dm），不会发到玩家群。</p>
     */
    public void handleGeoIpLookupFailure(String playerName, String ipAddress) {
        sendGeoIpAlert("IP地址解析服务异常，已放行: " + playerName + "(" + ipAddress + ")", "geoip lookup failed");
    }

    /** 统一的 GeoIP 异常告警：写日志并路由 {@code exception_alert}（PRIVATE → 管理员私信）。 */
    private void sendGeoIpAlert(String message, String stackSummary) {
        server.logger().warning(message);
        // 限频只抑制私信：上游故障时避免每次登录都打扰管理员，日志始终保留完整现场
        if (!throttledNotifier.shouldRun(GEOIP_ALERT_THROTTLE_KEY, GEOIP_ALERT_THROTTLE_MS)) {
            return;
        }
        MessageEnvelope envelope = configs.renderEvent(
                "exception_alert", java.util.Map.of("message", message, "stack_summary", stackSummary));
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
        String playerName = listFormatter.line(player);
        boolean minusCurrent = (state == PlayerState.QUIT || state == PlayerState.KICK);
        int displayOnlineCount = onlinePlayerCount - (minusCurrent ? 1 : 0);
        StringBuilder listBuilder = new StringBuilder();
        for (Player p : onlinePlayers) {
            if (minusCurrent && p.getUniqueId().equals(player.getUniqueId())) continue;
            listBuilder.append(listFormatter.line(p)).append("\n");
        }
        org.bukkit.Location loc = player.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        TemplateOptions opt = configs.templateOptions();
        // 权限组中文名统一走 RankService.groupDisplayName（唯一事实源），
        // 不再用模板系统的 role_alias 配置（已删除：与权限组映射重复维护）
        java.util.Map<String, String> vars = CoordFormatter.format(loc, opt);
        vars.put("world", world); // 覆盖 CoordFormatter 的别名，保留原始世界名
        vars.put("name", playerName);
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
    }
}
