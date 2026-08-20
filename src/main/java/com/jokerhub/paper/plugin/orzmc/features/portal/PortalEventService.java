package com.jokerhub.paper.plugin.orzmc.features.portal;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalPort;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerAuthenticationService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;

public final class PortalEventService {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("OrzMC.PortalEvent");
    /** 跨服 transfer 触发冷却（毫秒）：防 PlayerPortalEvent + PlayerMoveEvent 双路径重复触发。 */
    private static final long TRANSFER_COOLDOWN_MS = 5000;

    /** Folia 下 PlayerPortalEvent 不触发（2026-08-18 反编译 folia-26.2.jar 实证：callPlayerPortalEvent 无调用者），
     * 需用 PlayerMoveEvent 区域检测补偿；Paper 上保持原事件路径。 */
    private static final boolean FOLIA = isFolia();

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private final ServerFacade server;
    private final PortalPort portalService;
    private final Predicate<Player> authCheck;
    private final boolean folia;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastTransfer = new ConcurrentHashMap<>();

    public PortalEventService(ServerFacade server, PortalPort portalService) {
        this(server, portalService, FOLIA);
    }

    /** 测试用：可注入 folia 模式（真实环境默认自动检测）。 */
    PortalEventService(ServerFacade server, PortalPort portalService, boolean folia) {
        this(server, portalService, folia, new PlayerAuthenticationService()::isAuthenticated);
    }

    /** 测试用：可注入 folia 模式 + 认证决策（测试环境无 LoginSecurity，默认实现恒 true 无法覆盖未认证分支）。 */
    PortalEventService(ServerFacade server, PortalPort portalService, boolean folia, Predicate<Player> authCheck) {
        this(server, portalService, folia, authCheck, System::currentTimeMillis);
    }

    /** 测试用：注入可控时钟验证冷却滑动，避免真实 sleep。 */
    PortalEventService(
            ServerFacade server,
            PortalPort portalService,
            boolean folia,
            Predicate<Player> authCheck,
            LongSupplier clock) {
        this.server = server;
        this.portalService = portalService;
        this.authCheck = authCheck;
        this.folia = folia;
        this.clock = clock;
    }

    /** Paper 路径：PlayerPortalEvent（玩家即将传送门传送）。 */
    public void handle(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        // 冷却内不接管本次事件：避免「取消了原版传送但 transfer 被冷却拦截」的状态分歧
        if (isOnCooldown(player, clock.getAsLong())) {
            return;
        }
        // 检查玩家是否已认证
        if (!authCheck.test(player)) {
            // 未登录时，不进行传送
            event.setCancelled(true);
            return;
        }

        Location from = event.getFrom();
        String target = portalService.findTarget(from);
        if (target == null) return;
        event.setCancelled(true);
        transfer(player, target);
    }

    /**
     * Folia 补偿路径：PlayerMoveEvent 区域检测（PlayerPortalEvent 在 Folia 26.2 不触发）。
     *
     * <p>进入检测：仅当玩家方块坐标变化（真正走进传送门）才检查，站在传送门内不动不重复触发；
     * 命中传送门内部区域 → 认证通过 → transfer。未认证玩家不拦截（登录插件自行保护）。</p>
     */
    public void handleMove(PlayerMoveEvent event) {
        if (!folia) {
            return;
        }
        // 尊重其他插件（反作弊/区域防护）对本次移动的取消，避免按「意图位置」误触发 transfer
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null) {
            return;
        }
        // 只有方块坐标变化才算「走进」，避免站在传送门内每 tick 重复触发
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        // 冷却内快速跳过（transfer() 内为双路径共享的权威判断）
        if (isOnCooldown(player, clock.getAsLong())) {
            return;
        }
        // 精确命中传送门内部格（水平无邻域容差）：move 路径对每个方块移动求值，
        // 带容差的 findTarget 会把水平触发区膨胀为「门 + 四周 1 格」，路过玩家会被误 transfer。
        // 垂直方向按玩家身体两格匹配（脚底 + 躯干）：地面传送门内部格从脚底+1 起（cy=baseY+2），
        // 仅查脚底格会永远差 1 格导致补偿路径静默失效。
        // 命中后再做认证反射检查——避免全服玩家每移动一格都触发跨插件反射链（LoginSecurity）
        String target = findPortalTarget(to);
        if (target == null) {
            return;
        }
        if (!authCheck.test(player)) {
            return;
        }
        transfer(player, target);
    }

    private String findPortalTarget(Location feet) {
        String target = portalService.findTargetExact(feet);
        if (target != null) {
            return target;
        }
        // 躯干格（脚 +1）：玩家身体占两格，覆盖地面传送门（内部格从脚底+1 起）；水平仍精确
        Location torso = new Location(feet.getWorld(), feet.getX(), feet.getBlockY() + 1, feet.getZ());
        return portalService.findTargetExact(torso);
    }

    private void transfer(Player player, String target) {
        // 双路径共享冷却（权威判断）：防 PlayerPortalEvent + PlayerMoveEvent 重复触发
        long now = clock.getAsLong();
        if (isOnCooldown(player, now)) {
            return;
        }
        lastTransfer.put(player.getUniqueId(), now);
        String[] parts = target.split(":");
        String host = parts[0];
        String port = parts.length > 1 ? parts[1] : "25565";
        String cmd = "transfer " + host + " " + port + " " + player.getName();
        // Folia 上命令须在 global region 线程派发；捕获执行结果，失败时打 WARNING（避免静默失败，
        // 叠加「冷却内放行原版传送」造成双重困惑——玩家被取消一次后第二次直接进下界且无任何反馈）
        server.runSync(() -> {
            ServerFacade.ConsoleCommandResult result = server.executeConsoleCommand(cmd);
            if (result != null && !result.dispatched()) {
                LOGGER.warning("跨服 transfer 命令执行失败: " + cmd + " -> " + result.message());
            }
        });
    }

    private boolean isOnCooldown(Player player, long now) {
        Long last = lastTransfer.get(player.getUniqueId());
        if (last == null) {
            return false;
        }
        if (now - last >= TRANSFER_COOLDOWN_MS) {
            // 冷却已过期：移除条目，避免玩家用过一次传送门后永久残留
            lastTransfer.remove(player.getUniqueId());
            return false;
        }
        return true;
    }
}
