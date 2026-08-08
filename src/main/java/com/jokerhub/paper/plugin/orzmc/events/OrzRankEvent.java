package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 晋升检查监听：玩家上线时检查是否达到自动晋升条件。
 *
 * <p>时长数据直接读服务器原生 stats（离线也有数据），因此无需监听退出累计；
 * 上线检查 + 定时/审核时检查即可覆盖全部晋升时机。</p>
 */
public final class OrzRankEvent extends OrzBaseListener {

    private final RankService service;

    public OrzRankEvent(OrzMC plugin, RankService service) {
        super(plugin);
        this.service = service;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 异步执行晋升检查：LP loadUser（离线 3s 超时）与 stats 读取都可能较慢，
        // 不能阻塞主线程（PlayerJoinEvent 是主线程事件）。
        // 晋升/降级本身经 LuckPermsPromoter 的 scheduler 回主线程执行 LP 变更，线程安全。
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                service.checkPromotion(event.getPlayer().getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "晋升检查异常", e);
            }
        });
    }
}
