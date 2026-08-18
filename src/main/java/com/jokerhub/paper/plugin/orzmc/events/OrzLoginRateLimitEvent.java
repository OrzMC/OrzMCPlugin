package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.security.LoginRateLimitEventService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 进服限流/反 bot 监听器（安全加固 P2-2）。
 *
 * <p>{@link EventPriority#LOW} 在 {@code OrzPlayerEvent}（NORMAL）之前介入：频率/并发超限即
 * {@code disallow}，且早于 GeoIP 查询拦截，减少对查询服务的无效压力；
 * {@link PlayerJoinEvent}/{@link PlayerQuitEvent} 维护同 IP 并发计数。</p>
 */
public class OrzLoginRateLimitEvent extends OrzBaseListener {

    private final LoginRateLimitEventService service;

    public OrzLoginRateLimitEvent(OrzMC plugin, LoginRateLimitEventService service) {
        super(plugin);
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        service.onPlayerPreLogin(event);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        service.onPlayerJoin(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        service.onPlayerQuit(event);
    }
}
