package com.jokerhub.paper.plugin.orzmc.features.chat;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 聊天反垃圾事件编排（安全加固 P2-1）。
 *
 * <p>把 {@link ChatSpamFilterService} 的纯判定接入 {@link AsyncChatEvent} 事件侧：
 * 命中即取消事件（阻止全局广播），并以 warn 色向玩家发送配置的提示文案；
 * {@link PlayerQuitEvent} 时清理该玩家状态，避免内存无界增长。</p>
 */
public final class ChatSpamFilterEventService {

    private final ChatSpamFilterService filter;
    private final TypedConfigProvider configs;
    private final OrzTextStyles styles;

    public ChatSpamFilterEventService(ChatSpamFilterService filter, TypedConfigProvider configs, OrzTextStyles styles) {
        this.filter = filter;
        this.configs = configs;
        this.styles = styles;
    }

    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (filter.isSpam(player.getUniqueId(), plain)) {
            event.setCancelled(true);
            player.sendMessage(styles.warn(configs.chat().message()));
        }
    }

    public void onPlayerQuit(PlayerQuitEvent event) {
        filter.clear(event.getPlayer().getUniqueId());
    }
}
