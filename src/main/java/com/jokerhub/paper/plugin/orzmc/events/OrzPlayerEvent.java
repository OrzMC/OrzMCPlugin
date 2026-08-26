package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.features.player.LoginAccessControlService;
import com.jokerhub.paper.plugin.orzmc.features.player.PlayerEventService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class OrzPlayerEvent extends OrzBaseListener {
    private final LoginAccessControlService loginAccessControlService;
    private final PlayerEventService service;
    private final GuideService guideService;

    public OrzPlayerEvent(
            OrzMC plugin,
            LoginAccessControlService loginAccessControlService,
            GuideService guideService,
            PlayerEventService service) {
        super(plugin);
        this.loginAccessControlService = loginAccessControlService;
        this.service = service;
        this.guideService = guideService;
    }

    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        loginAccessControlService.handlePreLogin(event);
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
