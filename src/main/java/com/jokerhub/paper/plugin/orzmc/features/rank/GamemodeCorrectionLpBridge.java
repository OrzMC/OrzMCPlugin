package com.jokerhub.paper.plugin.orzmc.features.rank;

import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.track.UserDemoteEvent;
import net.luckperms.api.event.user.track.UserPromoteEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 权限组升降级 → 游戏模式矫正桥（LP 专属，软依赖条件实例化）。
 *
 * <p>订阅 LuckPerms {@link UserPromoteEvent} / {@link UserDemoteEvent}（track 升降级，
 * 覆盖审核晋升、{@code /rank} 手动升降级等 LP track 操作；手动 {@code lp user X parent set}
 * 不触发 track 事件，由登录兜底通道覆盖），若在线玩家当前游戏模式已无对应权限，
 * 自动切回生存——是权限组变化后的兜底通道。</p>
 *
 * <p><b>软依赖加载约束</b>：本类直接引用 {@code net.luckperms.api} 类型，仅在 LP 已启用时由
 * 装配层实例化（同 {@link LuckPermsPromoter} 模式）——LP 缺失时本类不会被加载，
 * 不会触发 NoClassDefFoundError。订阅传 {@code plugin}，LP 在插件禁用时自动注销。</p>
 *
 * <p><b>线程模型</b>：LP 事件可发自异步线程；setGameMode/teleport 必须回同步调度线程，
 * 经 {@link ServerFacade#runSync} 执行（Folia global region / Paper 主线程）。</p>
 */
public final class GamemodeCorrectionLpBridge {

    private static final Logger LOGGER = Logger.getLogger("OrzMC.GamemodeCorrectionLP");

    /** 持有订阅引用作生命周期锚点（LP 因传入 plugin 亦会在插件禁用时自动关闭）。 */
    private final List<EventSubscription<?>> subscriptions;

    public GamemodeCorrectionLpBridge(
            JavaPlugin plugin, ServerFacade serverFacade, LuckPerms api, GamemodeCorrectionService correctionService) {
        this.subscriptions = List.of(
                api.getEventBus()
                        .subscribe(
                                plugin,
                                UserPromoteEvent.class,
                                event -> handle(
                                        serverFacade,
                                        correctionService,
                                        event.getUser().getUniqueId())),
                api.getEventBus()
                        .subscribe(
                                plugin,
                                UserDemoteEvent.class,
                                event -> handle(
                                        serverFacade,
                                        correctionService,
                                        event.getUser().getUniqueId())));
    }

    private static void handle(ServerFacade serverFacade, GamemodeCorrectionService correctionService, UUID playerId) {
        // LP 事件可发自异步线程；correctAsync 经玩家实体调度器投递到其 region 线程（Folia 兼容）
        correctionService.correctAsync(org.bukkit.Bukkit.getPlayer(playerId));
    }
}
