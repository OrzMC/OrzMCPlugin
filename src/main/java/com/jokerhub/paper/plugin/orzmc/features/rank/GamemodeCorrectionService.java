package com.jokerhub.paper.plugin.orzmc.features.rank;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.GamemodeCorrectionConfig;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * 游戏模式矫正：玩家权限组变化后，把已无权限的游戏模式自动切回生存。
 *
 * <p><b>问题</b>：builder 可切创造、admin 可切观察；被降级回 default/member 后
 * Essentials gamemode 权限被回收，但游戏模式残留（仍创造/观察）——与当前组允许的
 * 模式不匹配，形成权限漏洞（普通玩家保有创造能力）。本服务把游戏模式与权限对齐。</p>
 *
 * <p><b>判定</b>：仅读 {@code player.hasPermission}（LuckPerms 是 Bukkit 权限提供者，
 * 同步安全，零额外 LP API 往返）。CREATIVE→无 essentials.gamemode.creative 权限、
 * SPECTATOR→无 essentials.gamemode.spectator 权限、ADVENTURE→无 essentials.gamemode.adventure
 * 权限，均切 SURVIVAL；SURVIVAL 不处理。SPECTATOR 玩家切生存前先处理安全位置
 * （低于世界 {@code minHeight+10} 或脚下不安全 → 传回床点/世界出生点），防虚空坠落/墙内窒息。</p>
 *
 * <p><b>线程模型</b>：{@link #correctIfNeeded(Player)} / {@link #correctIfNeeded(UUID)}
 * 必须由调用方保证在<b>同步调度线程</b>执行（Bukkit setGameMode/teleport/sendMessage 线程约束）。
 * 异步上下文触发一律先 {@code ServerFacade.runSync} 再调用（见 GamemodeCorrectionLpBridge、
 * 登录兜底监听器与 RankService 升降级后的装配）。</p>
 *
 * <p><b>防抖</b>：{@code ConcurrentHashMap.compute} 原子更新上次矫正时间，
 * 配置 {@code debounce-ms} 内同一玩家不重复矫正（LP 权限重算事件可能连发；
 * check-then-act 有竞态，须原子判定+更新）。</p>
 */
public final class GamemodeCorrectionService {

    private static final Logger LOGGER = Logger.getLogger("OrzMC.GamemodeCorrection");

    /** 矫正成功后的玩家提示文案。 */
    public static final String FIX_MESSAGE = "你的权限组已变化，游戏模式已切换为生存模式。";

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final Supplier<GamemodeCorrectionConfig> configSupplier;
    private final OrzTextStyles styles;
    /** 玩家 UUID → 上次矫正时间戳（防抖，compute 原子更新）。 */
    private final Map<UUID, Long> lastCorrected = new ConcurrentHashMap<>();

    public GamemodeCorrectionService(
            org.bukkit.plugin.java.JavaPlugin plugin,
            Supplier<GamemodeCorrectionConfig> configSupplier,
            OrzTextStyles styles) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.styles = styles;
    }

    /** 异步矫正：经玩家所属实体调度器投递（Folia 实体操作必须在该实体 region 线程），线程无关可安全从任意上下文调用。 */
    public void correctAsync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.getScheduler()
                .execute(
                        plugin,
                        () -> {
                            try {
                                correctIfNeeded(player);
                            } catch (RuntimeException e) {
                                LOGGER.log(Level.WARNING, "异步游戏模式矫正失败: " + player.getUniqueId(), e);
                            }
                        },
                        () -> {},
                        0L);
    }

    /** 矫正指定玩家；返回是否发生了矫正。必须在同步调度线程调用；玩家离线/无效则跳过。 */
    public boolean correctIfNeeded(Player player) {
        GamemodeCorrectionConfig config = configSupplier.get();
        if (config == null || !config.enabled()) {
            return false; // 配置缺失/关闭：fail-closed，不做任何动作
        }
        if (player == null || !player.isOnline()) {
            return false;
        }
        String permission = permissionFor(player.getGameMode());
        if (permission == null) {
            return false; // SURVIVAL / 未知模式：无需矫正
        }
        if (player.hasPermission(permission)) {
            return false; // 有对应模式权限：允许当前模式
        }
        if (!acquireDebounce(player.getUniqueId())) {
            return false; // 防抖窗口内：不重复矫正
        }
        if (player.getGameMode() == GameMode.SPECTATOR && config.teleportToSpawnOnSpectatorFix()) {
            ensureSafeLocation(player);
        }
        return switchToSurvival(player);
    }

    /** 矫正指定 UUID 的在线玩家（离线/未登录跳过）。必须在同步调度线程调用。 */
    public boolean correctIfNeeded(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return correctIfNeeded(Bukkit.getPlayer(playerId));
    }

    /** 游戏模式 → 对应 Essentials gamemode 权限；SURVIVAL/未知返回 null（无需矫正）。 */
    private static String permissionFor(GameMode mode) {
        return switch (mode) {
            case CREATIVE -> "essentials.gamemode.creative";
            case SPECTATOR -> "essentials.gamemode.spectator";
            case ADVENTURE -> "essentials.gamemode.adventure";
            case SURVIVAL -> null;
        };
    }

    /** 原子防抖判定+更新：窗口内返回 false（不矫正），否则记录本次时间返回 true。 */
    private boolean acquireDebounce(UUID id) {
        long debounceMs = configSupplier.get().debounceMs();
        if (debounceMs <= 0) {
            return true; // 防抖关闭（0/负 → 恒放行）
        }
        long now = System.currentTimeMillis();
        boolean[] acquired = {false};
        lastCorrected.compute(id, (k, last) -> {
            if (last != null && now - last < debounceMs) {
                return last; // 窗口内：保留原时间，不矫正
            }
            acquired[0] = true;
            return now;
        });
        return acquired[0];
    }

    /** 切生存 + 玩家提示；返回是否发生了矫正（恒 true）。 */
    private boolean switchToSurvival(Player player) {
        try {
            player.setGameMode(GameMode.SURVIVAL);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "切换玩家生存模式失败: " + player.getUniqueId(), e);
            return false;
        }
        player.sendMessage(styles.info(FIX_MESSAGE));
        return true;
    }

    /** SPECTATOR 切生存前的安全位置处理：位置不安全则传回床点（null 用世界出生点）。 */
    private void ensureSafeLocation(Player player) {
        if (isSafeLocation(player)) {
            return;
        }
        Location spawn = player.getBedSpawnLocation();
        if (spawn == null) {
            World world = player.getWorld();
            spawn = world == null ? null : world.getSpawnLocation();
        }
        if (spawn != null) {
            player.teleport(spawn);
        }
    }

    /** 位置是否安全：不低于世界 {@code minHeight+10}，且脚下有支撑（实心非流体）、脚底所在方块可通过。 */
    private static boolean isSafeLocation(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return false; // 无世界视为不安全，兜底回出生点
        }
        if (loc.getY() < world.getMinHeight() + 10) {
            return false; // 接近/低于虚空：切生存会坠落
        }
        Block below = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        Material belowType = below.getType();
        boolean belowSafe = belowType.isSolid() && belowType != Material.LAVA && belowType != Material.WATER;
        if (!belowSafe) {
            return false; // 悬空/流体上：切生存会坠落或灼烧/溺亡
        }
        Block at = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return at.isPassable(); // 脚底所在方块须可通过（防卡墙内窒息）
    }
}
