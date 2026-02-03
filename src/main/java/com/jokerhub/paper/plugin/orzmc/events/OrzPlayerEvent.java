package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.player.PlayerEventService;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class OrzPlayerEvent extends OrzBaseListener {
    private final GeoIpAccessService geoIpAccessService;
    private final PlayerEventService service;
    private final GuideService guideService;
    private final OrzTextStyles styles;

    public OrzPlayerEvent(
            OrzMC plugin,
            ConfigService configService,
            OrzTextStyles styles,
            Notifier notifier,
            ThrottledNotifier throttledNotifier) {
        super(plugin);
        this.geoIpAccessService = new GeoIpAccessService(configService);
        this.service = new PlayerEventService(configService, styles, notifier, throttledNotifier);
        this.guideService = new GuideService(configService, styles);
        this.styles = styles;
    }

    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (WorldMaintenanceService.isRunningGlobal()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, styles.warn("服务器地图备份中，请稍后再尝试登录。"));
            return;
        }
        if (!event.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.ALLOWED)) {
            return;
        }
        String ipAddress = event.getAddress().getHostAddress();
        String playerName = event.getPlayerProfile().getName();
        if (ipAddress.isEmpty()) {
            return;
        }
        geoIpAccessService
                .decide(ipAddress)
                .thenAccept(decision -> {
                    service.handleGeoIpDecision(event, playerName, ipAddress, decision);
                })
                .exceptionally(e -> {
                    service.handleGeoIpException(e);
                    return null;
                });
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

    @EventHandler
    public void onPlayerKickLeave(PlayerKickEvent event) {
        service.notifyPlayerState(event.getPlayer(), PlayerEventService.PlayerState.KICK);
    }
}
