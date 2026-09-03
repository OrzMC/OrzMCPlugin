package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.prison.PrisonService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 坐牢玩家登录监听：重进仍保持 prison 组，强制传回牢房（防逃跑/防绕管）。
 *
 * <p>坐牢 = LP 组独立（不在四级 track），因此 {@code OrzRankEvent} 的自动晋升检查
 * 对 prison 玩家直接跳过（RankService.checkPromotion 已拦截）；此处只做传送兜底。</p>
 *
 * <p>双保险：join 当下立即查（在线缓存命中则即刻传回）+ 延迟约 1 秒再查一次——
 * 覆盖 join 瞬间 LP 在线缓存未命中 / 牢房世界尚未加载导致第一次传送落空的本局游离场景
 * （与 OrzGamemodeCorrectionEvent 的登录兜底同构）。</p>
 */
public final class OrzPrisonEvent extends OrzBaseListener {

    /** 延迟 tick：约 1 秒（等 LP 数据加载完成 + 牢房世界就绪后再兜底一次）。 */
    private static final long REJOIN_DELAY_TICKS = 20L;

    private final PrisonService service;

    public OrzPrisonEvent(OrzMC plugin, PrisonService service) {
        super(plugin);
        this.service = service;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        // isPrisoner 在线玩家读 LP 在线缓存（零 future 等待，同步安全）；坐牢则传回牢房
        if (service.isPrisoner(player.getUniqueId())) {
            service.teleportToCell(player.getUniqueId());
        }
        // 延迟兜底：join 后 LP 缓存/牢房世界可能未就绪，~1s 后重查一次并强制传回
        serverFacade()
                .runLater(
                        () -> {
                            // teleportToCell 内部经玩家实体调度器投递（Folia 实体操作线程约束）
                            if (player.isOnline() && service.isPrisoner(player.getUniqueId())) {
                                service.teleportToCell(player.getUniqueId());
                            }
                        },
                        REJOIN_DELAY_TICKS);
    }
}
