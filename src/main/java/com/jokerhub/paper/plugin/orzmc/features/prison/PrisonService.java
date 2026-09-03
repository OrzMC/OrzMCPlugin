package com.jokerhub.paper.plugin.orzmc.features.prison;

import com.jokerhub.paper.plugin.orzmc.features.rank.PlayerNameResolver;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.PrisonConfig;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 坐牢服务：作弊玩家强制进入独立 prison 组（禁用全部权限，仅保留 essentials.msg 私聊）。
 *
 * <p><b>prison 完全独立于四级 track</b>：坐牢 = LP parent/primary 全部切换为 prison 组，
 * 原组/原位置写入 LP 用户元数据；解除坐牢 = 恢复原组并传送回原位置（原位置缺失回出生点）。
 * 坐牢玩家重进仍保持 prison（不触发任何四级晋升/回归），由 {@code OrzPrisonEvent} 强制传回牢房。</p>
 *
 * <p><b>线程模型</b>：LP 操作（{@link PrisonLpGateway#imprison}/{@link PrisonLpGateway#release}）
 * 在异步执行器执行；玩家实体操作（读位置/传送）经 {@link Player#getScheduler()} 投递到玩家所属
 * region 线程（Folia 实体操作线程约束），调用线程不阻塞。</p>
 */
public final class PrisonService {

    private final PrisonLpGateway gateway;
    private final JavaPlugin plugin;
    private final Supplier<PrisonConfig> config;
    private final OrzTextStyles styles;
    private final ReviewNotifier notifier;
    private final PlayerNameResolver nameResolver;

    public PrisonService(
            PrisonLpGateway gateway,
            JavaPlugin plugin,
            Supplier<PrisonConfig> config,
            OrzTextStyles styles,
            ReviewNotifier notifier,
            PlayerNameResolver nameResolver) {
        this.gateway = gateway;
        this.plugin = plugin;
        this.config = config;
        this.styles = styles;
        this.notifier = notifier;
        this.nameResolver = nameResolver;
    }

    /** 玩家当前是否坐牢（LP 可用且 gateway 判定为 prison 组）。 */
    public boolean isPrisoner(UUID playerId) {
        return gateway.isAvailable() && gateway.isPrisoner(playerId);
    }

    /**
     * 坐牢：记录原组 + 原位置（在线）→ LP 切换 prison 组 → 在线玩家传送牢房 + 通知。
     *
     * @return 完成时给出业务结果（成功/失败文案）
     */
    public CompletableFuture<Result> imprison(UUID playerId) {
        if (!gateway.isAvailable()) {
            return CompletableFuture.completedFuture(new Result.Failure(styles.error("LuckPerms 不可用，无法执行坐牢操作")));
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            // 离线坐牢：无当前位置可记录、无需传送
            return gateway.imprison(playerId, null).thenApply(outcome -> finalizeImprison(playerId, player, outcome));
        }
        return readLocationAsync(player)
                .thenCompose(loc -> gateway.imprison(playerId, loc))
                .thenApply(outcome -> finalizeImprison(playerId, player, outcome));
    }

    /**
     * 解除坐牢：LP 恢复原组并清除元数据 → 在线玩家传送回原位置（缺失回出生点）+ 通知。
     *
     * @return 完成时给出业务结果（成功/失败/不在牢房）
     */
    public CompletableFuture<Result> release(UUID playerId) {
        if (!gateway.isAvailable()) {
            return CompletableFuture.completedFuture(new Result.Failure(styles.error("LuckPerms 不可用，无法执行解除坐牢操作")));
        }
        return gateway.release(playerId).thenApply(outcome -> {
            if (!outcome.success()) {
                return new Result.Failure(styles.error("解除坐牢失败（数据写入失败，请重试或联系管理员）"));
            }
            if (!outcome.wasPrisoner()) {
                return new Result.Success(styles.info("该玩家不在牢房中"));
            }
            teleportToSavedLocation(playerId, outcome.originalLocation());
            notifier.gameMessage(playerId, "你已解除坐牢。");
            notifier.groupEvent(
                    TemplateKeys.PRISON_RELEASED,
                    Map.of(
                            "player",
                            playerName(playerId),
                            "group",
                            RankService.groupDisplayName(outcome.originalGroup())));
            return new Result.Success(styles.success("已解除 " + playerName(playerId) + " 的坐牢（恢复组 "
                    + RankService.groupDisplayName(outcome.originalGroup()) + "）"));
        });
    }

    /** 玩家重进时：坐牢玩家强制传回牢房（防逃跑）。无牢房配置/世界未加载回退当前世界出生点。 */
    public void teleportToCell(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        player.getScheduler()
                .execute(
                        plugin,
                        () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            Location cell = resolveCellLocation(player.getWorld());
                            if (cell != null) {
                                player.teleportAsync(cell);
                            }
                        },
                        () -> {},
                        0L);
    }

    private Result finalizeImprison(UUID playerId, Player player, PrisonLpGateway.ImprisonOutcome outcome) {
        if (!outcome.success()) {
            return new Result.Failure(styles.error("坐牢失败（数据写入失败，请重试或联系管理员）"));
        }
        if (player != null && player.isOnline()) {
            teleportToCell(playerId);
        }
        notifier.gameMessage(playerId, "你已被关入牢房。");
        notifier.groupEvent(
                TemplateKeys.PRISON_IMPRISONED,
                Map.of("player", playerName(playerId), "group", RankService.groupDisplayName(outcome.originalGroup())));
        return new Result.Success(styles.success("已将 " + playerName(playerId) + " 关入牢房（原组 "
                + RankService.groupDisplayName(outcome.originalGroup()) + "）"));
    }

    /** 传送回原位置（元数据里记录的位置）；原位置缺失/无效回出生点。 */
    private void teleportToSavedLocation(UUID playerId, String saved) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        player.getScheduler()
                .execute(
                        plugin,
                        () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            Location loc = parseLocation(saved);
                            if (loc == null) {
                                loc = player.getWorld().getSpawnLocation();
                            }
                            player.teleportAsync(loc);
                        },
                        () -> {},
                        0L);
    }

    /** 在实体调度器上读取当前位置（Folia 区域线程安全）；离线/异常返回 null。 */
    private CompletableFuture<String> readLocationAsync(Player player) {
        CompletableFuture<String> future = new CompletableFuture<>();
        player.getScheduler()
                .execute(
                        plugin,
                        () -> {
                            try {
                                future.complete(player.isOnline() ? serializeLocation(player.getLocation()) : null);
                            } catch (Throwable t) {
                                future.complete(null);
                            }
                        },
                        () -> future.complete(null),
                        0L);
        return future;
    }

    // ---- 位置序列化/解析 ----

    /** 序列化位置为 {@code world,x,y,z,yaw,pitch}。 */
    static String serializeLocation(Location loc) {
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw()
                + "," + loc.getPitch();
    }

    /**
     * 解析 {@code world,x,y,z[,yaw,pitch]}。
     *
     * <p>格式非法、世界未加载/不存在、y 超出世界高度范围 [minHeight, maxHeight] 一律返回 null
     * （调用方回退出生点）——防配置错写把玩家传进虚空/天花板外。</p>
     */
    static Location parseLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            if (y < world.getMinHeight() || y > world.getMaxHeight()) {
                return null;
            }
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 牢房位置：配置的 {@code cell_location}；未配置/世界未加载回退玩家当前世界出生点。 */
    private Location resolveCellLocation(World fallbackWorld) {
        PrisonConfig cfg = config.get();
        String raw = cfg == null ? null : cfg.cellLocation();
        if (raw == null || raw.isBlank()) {
            return fallbackWorld == null ? null : fallbackWorld.getSpawnLocation();
        }
        Location loc = parseLocation(raw);
        if (loc == null) {
            return fallbackWorld == null ? null : fallbackWorld.getSpawnLocation();
        }
        return loc;
    }

    private String playerName(UUID playerId) {
        String name = nameResolver == null ? null : nameResolver.resolve(playerId);
        return name == null || name.isBlank() ? playerId.toString() : name;
    }

    /** 业务结果（成功/失败文案）。 */
    public sealed interface Result permits Result.Success, Result.Failure {
        record Success(Component message) implements Result {}

        record Failure(Component message) implements Result {}
    }
}
