package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.chat.ChatSpamFilterEventService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 聊天反垃圾监听器（安全加固 P2-1）。
 *
 * <p>{@link EventPriority#LOWEST} 最早介入：命中刷屏/广告即取消 {@link AsyncChatEvent}，
 * 阻止消息进入全局广播；{@link PlayerQuitEvent} 时清理该玩家限流/重复状态。</p>
 */
public class OrzChatEvent extends OrzBaseListener {

    private final ChatSpamFilterEventService service;

    public OrzChatEvent(OrzMC plugin, ChatSpamFilterEventService service) {
        super(plugin);
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        service.onAsyncChat(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        service.onPlayerQuit(event);
    }
}
