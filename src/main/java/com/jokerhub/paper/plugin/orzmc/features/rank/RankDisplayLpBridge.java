package com.jokerhub.paper.plugin.orzmc.features.rank;

import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.track.UserTrackEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 等级变更 → 玩家名颜色实时刷新桥（LP 专属，软依赖条件实例化）。
 *
 * <p>订阅 LuckPerms {@link UserTrackEvent}（track promote/demote 触发，含插件内
 * {@code $p}/{@code /apply}/{@code /review} 与控制台手动 {@code lp user X promote rank}），
 * 等级变更即在调度线程刷新在线玩家三处颜色。</p>
 *
 * <p><b>软依赖加载约束</b>：本类直接引用 {@code net.luckperms.api} 类型，仅在 LP 已启用时由
 * 装配层实例化（同 {@link LuckPermsPromoter} 模式）——LP 缺失时本类不会被加载，
 * 不会触发 NoClassDefFoundError。订阅传 {@code plugin}，LP 在插件禁用时自动注销。</p>
 */
public final class RankDisplayLpBridge {

    private static final Logger LOGGER = Logger.getLogger("OrzMC.RankDisplayLP");

    /** 持有订阅引用作生命周期锚点（LP 因传入 plugin 亦会在插件禁用时自动关闭）。 */
    private final EventSubscription<UserTrackEvent> subscription;

    public RankDisplayLpBridge(
            JavaPlugin plugin, ServerFacade serverFacade, LuckPerms api, PlayerRankDisplayService displayService) {
        this.subscription = api.getEventBus().subscribe(plugin, UserTrackEvent.class, event -> {
            // LP 事件可发自异步线程；计分板/Tab 变更必须回到调度线程执行
            serverFacade.runSync(() -> {
                try {
                    displayService.refresh(event.getUser().getUniqueId());
                } catch (RuntimeException e) {
                    LOGGER.log(
                            Level.WARNING, "等级变更刷新玩家名颜色失败: " + event.getUser().getUniqueId(), e);
                }
            });
        });
    }
}
