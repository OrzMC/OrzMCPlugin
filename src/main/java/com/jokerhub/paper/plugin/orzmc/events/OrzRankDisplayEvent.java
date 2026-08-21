package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.rank.PlayerRankDisplayService;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家名颜色显示监听器（按权限等级，三处统一着色）。
 *
 * <ul>
 *   <li>聊天：{@link AsyncChatEvent} 用 {@link ChatRenderer#viewerUnaware} 给玩家名着色。
 *       名字取 displayName 纯文本（保留 EssentialsX {@code /nick} 等昵称）再强制 rank 色。
 *       <b>让位语义</b>：在 {@link EventPriority#LOWEST} 设渲染器，若存在聊天格式化插件
 *       （如 EssentialsChat）会在更高优先级覆盖本渲染器 → 聊天格式归对方，rank 色体现在
 *       Tab + 头顶名牌；无则本渲染器保留 → 聊天着色。</li>
 *   <li>上线：调度线程应用头顶队伍 + Tab 名；再延迟 1 tick 兜底 LP 在线缓存未就绪</li>
 *   <li>下线：调度线程清理 orzmc-* 队伍成员</li>
 * </ul>
 */
public final class OrzRankDisplayEvent extends OrzBaseListener {

    private final PlayerRankDisplayService service;

    public OrzRankDisplayEvent(OrzMC plugin, PlayerRankDisplayService service) {
        super(plugin);
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        NamedTextColor color = service.colorFor(event.getPlayer());
        if (color == null) {
            return;
        }
        // 镜像默认渲染器（chat.type.text），仅把玩家名改成等级色。
        // 名字用 displayName 纯文本：保留昵称（EssentialsX /nick），强制统一 rank 色（纯色不加前缀）。
        // LOWEST 设渲染器：聊天格式化插件（如 EssentialsChat）在更高优先级覆盖即让位；
        // 不用「renderer != defaultRenderer」检测——Paper 的 defaultRenderer() 每次新建实例，身份比较不可靠。
        event.renderer(ChatRenderer.viewerUnaware((viewer, displayName, message) -> Component.translatable(
                "chat.type.text",
                Component.text(PlainTextComponentSerializer.plainText().serialize(displayName))
                        .color(color),
                message)));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        serverFacade().runSync(() -> service.applyTo(event.getPlayer()));
        // 兜底：上线瞬间 LP 在线缓存可能未就绪（currentGroup 落到 default），1 秒后再刷一次
        serverFacade().runLater(() -> service.applyTo(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        serverFacade().runSync(() -> service.removeFor(event.getPlayer()));
    }
}
