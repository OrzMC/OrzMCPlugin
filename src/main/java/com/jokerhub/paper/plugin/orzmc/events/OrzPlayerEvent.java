package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.player.PlayerEventService;
import com.jokerhub.paper.plugin.orzmc.features.security.BlacklistService;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class OrzPlayerEvent extends OrzBaseListener {
    private final GeoIpAccessService geoIpAccessService;
    private final BlacklistService blacklistService;
    private final PlayerEventService service;
    private final GuideService guideService;
    private final OrzTextStyles styles;
    private final WorldMaintenanceService maintenanceService;

    public OrzPlayerEvent(
            OrzMC plugin,
            GeoIpAccessService geoIpAccessService,
            BlacklistService blacklistService,
            PlayerEventService service,
            GuideService guideService,
            OrzTextStyles styles,
            WorldMaintenanceService maintenanceService) {
        super(plugin);
        this.geoIpAccessService = geoIpAccessService;
        this.blacklistService = blacklistService;
        this.service = service;
        this.guideService = guideService;
        this.styles = styles;
        this.maintenanceService = maintenanceService;
    }

    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (maintenanceService.isRunning()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, styles.warn("服务器地图备份中，请稍后再尝试登录。"));
            return;
        }
        if (!event.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.ALLOWED)) {
            return;
        }
        String ipAddress = event.getAddress().getHostAddress();
        if (blacklistService.isBlocked(ipAddress)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, styles.error("你的IP已被禁止访问"));
            return;
        }
        String playerName = event.getPlayerProfile().getName();
        if (ipAddress.isEmpty()) {
            return;
        }
        // 阻塞等待本次查询结果：只在异步处理器线程上等待，不阻塞主线程；
        // 超时/异常由 handleGeoIpPreLogin 内部按 fail-open 放行并告警。
        service.handleGeoIpPreLogin(
                event,
                playerName,
                ipAddress,
                geoIpAccessService.decide(ipAddress),
                GeoIpAccessService.DECISION_TIMEOUT_MS);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        guideService.giveIfFirstJoin(event.getPlayer());
        service.notifyPlayerState(event.getPlayer(), PlayerEventService.PlayerState.JOIN);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        service.notifyPlayerState(event.getPlayer(), PlayerEventService.PlayerState.QUIT);
    }

    // MONITOR：观察最终取消状态——若在 LOW/HIGHEST 等早期优先级被其他插件取消，
    // NORMAL 默认优先级下的 isCancelled() 尚未反映最终结果，会误发「被踢」通知。
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKickLeave(PlayerKickEvent event) {
        if (event.isCancelled()) {
            // 被其他插件取消的踢人：玩家仍在线上，不产生「被踢」通知
            return;
        }
        service.notifyPlayerState(event.getPlayer(), PlayerEventService.PlayerState.KICK);
    }
}
