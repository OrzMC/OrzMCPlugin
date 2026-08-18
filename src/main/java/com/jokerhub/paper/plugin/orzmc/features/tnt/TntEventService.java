package com.jokerhub.paper.plugin.orzmc.features.tnt;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.player.PlayerDisplayNames;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.CoordFormatter;
import io.papermc.paper.event.block.BlockPreDispenseEvent;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TntEventService {
    /** 方块爆炸统一归并到该标签，避免一次大爆炸按方块材质（STONE/DIRT/...）拆分刷屏。 */
    private static final String BLOCK_EXPLODE_LABEL = "方块爆炸";

    /** 聚合区域水平边长（方块数）：128 = 8×8 区块，覆盖一次大型爆炸的横向跨度。 */
    private static final int REGION_SIZE_BLOCKS = 128;

    /** 聚合区域垂直跨度（方块数）：64 ≈ 常见一层建筑高度。同 XZ 立柱但不同高度层的爆炸分开聚合，告警坐标才可行动。 */
    private static final int REGION_VERTICAL_BLOCKS = 64;

    private static final List<String> DEFAULT_EXEMPT_ENTITIES = List.of(
            "CREEPER",
            "FIREBALL",
            "BREEZE",
            "WIND_CHARGE",
            "BREEZE_WIND_CHARGE",
            "ENDER_DRAGON",
            "END_CRYSTAL",
            "WITHER",
            "WITHER_SKULL",
            "SLIME",
            "STRAY");

    private final TypedConfigProvider configs;
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final OrzTextStyles styles;
    private final Notifier notifier;
    private final ServerScheduler scheduler;
    /**
     * 突发聚合状态：key=world|区域|消息类型 → 批次计数/首事件坐标。
     *
     * <p>Paper：事件与 runLater 均同步，单线程访问。Folia：爆炸/引燃事件在爆炸所在 chunk 的
     * region 线程触发，而聚合区域 128×128×64 方块跨越多个 chunk——不同 region 可并发命中同一 key，
     * 故用 {@code ConcurrentHashMap} + {@link #aggregateNotify} 内的 {@code compute} 原子化建表/计数，
     * 避免 get-then-put 竞态丢计数或建多个批次。</p>
     */
    private final Map<String, PendingAlert> pendingAlerts = new ConcurrentHashMap<>();

    public TntEventService(
            TypedConfigProvider configs, OrzTextStyles styles, Notifier notifier, ServerScheduler scheduler) {
        this.configs = configs;
        this.styles = styles;
        this.notifier = notifier;
        this.scheduler = scheduler;
    }

    /** 读时解析当前 TNT 策略；配置 reload 后自动取新值，无需重建服务。 */
    private TntPolicy currentPolicy() {
        return new TntPolicy(configs.tnt());
    }

    public void onTNTPrime(@NotNull TNTPrimeEvent event) {
        Block placedBlock = event.getBlock();
        TntPolicy policy = currentPolicy();
        if (!policy.isEnableTnt() && policy.isNotInWhiteList(placedBlock.getLocation())) {
            event.setCancelled(true);
            aggregateNotify(
                    placedBlock.getLocation(),
                    "TNT被点燃（已禁止）",
                    placedBlock.getType().name(),
                    false);
            return;
        }
        aggregateNotify(
                placedBlock.getLocation(), "TNT被点燃", placedBlock.getType().name(), false);
    }

    public void onPlaceBlock(@NotNull BlockPlaceEvent event) {
        Block placedBlock = event.getBlockPlaced();
        Material placedBlockType = placedBlock.getType();
        Player player = event.getPlayer();
        if (placedBlockType == Material.TNT) {
            handleTNTPlace(event, player, placedBlock);
            return;
        }
        if (placedBlockType == Material.RESPAWN_ANCHOR && !currentPolicy().isEnableRespawnAnchor()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("重生锚放置已被管理员禁用").color(TextColor.color(0xFF5555)));
        }
    }

    public void onBlockPreDispense(@NotNull BlockPreDispenseEvent event) {
        ItemStack itemStack = event.getItemStack();
        Material itemType = itemStack.getType();
        if (itemType != Material.TNT && itemType != Material.TNT_MINECART) {
            return;
        }
        Block dispenser = event.getBlock();
        TntPolicy policy = currentPolicy();
        if (!policy.isEnableTnt() && policy.isNotInWhiteList(dispenser.getLocation())) {
            event.setCancelled(true);
            aggregateNotify(
                    dispenser.getLocation(),
                    "发射" + itemType.name() + "被禁止",
                    dispenser.getType().name(),
                    false);
        }
    }

    public void onBlockExplode(@NotNull BlockExplodeEvent event) {
        Block block = event.getBlock();
        if (block.getType().isAir()) {
            return;
        }
        aggregateNotify(block.getLocation(), BLOCK_EXPLODE_LABEL, "EXPLOSION", true);
    }

    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        EntityType entityType = event.getEntityType();
        if (isExemptEntity(entityType)) {
            return;
        }
        aggregateNotify(event.getLocation(), entityType.name() + "爆炸", "EXPLOSION", true);
    }

    private void handleTNTPlace(BlockPlaceEvent event, Player player, Block placedBlock) {
        TntPolicy policy = currentPolicy();
        int tntPlaceCooldown = policy.getPlaceCooldownSeconds();
        if (tntPlaceCooldown > 0 && checkCooldown(player, tntPlaceCooldown)) {
            event.setCancelled(true);
            long remaining =
                    (playerCooldowns.get(player.getUniqueId()) + tntPlaceCooldown * 1000L - System.currentTimeMillis())
                            / 1000;
            player.sendMessage(Component.text()
                    .append(Component.text("放置TNT冷却中，请等待 "))
                    .append(Component.text(remaining + "秒").color(TextColor.color(0xFFAA00)))
                    .build());
            return;
        }
        if (!policy.isEnableTnt() && policy.isNotInWhiteList(placedBlock.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("TNT放置已被管理员禁用").color(TextColor.color(0xFF5555)));
            return;
        }
        if (tntPlaceCooldown > 0) {
            playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
        sendPlacementNotification(player, placedBlock);
    }

    private boolean checkCooldown(@NotNull Player player, int tntPlaceCooldown) {
        if (!playerCooldowns.containsKey(player.getUniqueId())) {
            return false;
        }
        long lastPlaceTime = playerCooldowns.get(player.getUniqueId());
        return System.currentTimeMillis() - lastPlaceTime < tntPlaceCooldown * 1000L;
    }

    /**
     * 突发聚合入口：同区域同类型事件在窗口内合并为一条告警。
     *
     * <p>批次事件不立即发送，统一在窗口尾部由 {@link #flushTail(String)} 冲刷为
     * 一条消息：多事件补发 {@code ×N} 汇总，单发事件补发不带次数的单条。
     * 避免「立即发送 + 尾部汇总」双条刷屏。</p>
     */
    private void aggregateNotify(Location location, String message, String blockType, boolean explosionPrefix) {
        String key = aggregateKey(location, message);
        // compute 原子化建表/计数：Folia 下不同 region 并发命中同一 key 时，
        // 首次创建（含调度窗口尾部冲刷）或累计计数均只执行一次，不丢计数。
        pendingAlerts.compute(key, (k, existing) -> {
            if (existing == null) {
                PendingAlert created = new PendingAlert();
                created.epicenter = location;
                created.message = message;
                created.blockType = blockType;
                created.explosionPrefix = explosionPrefix;
                created.count = 1;
                // 调度在 compute 返回（入表可见）之前完成：中途抛异常不落表，
                // 不留「无调度冲刷的孤儿条目」，避免该 key 永久静默且 map 无界增长。
                long windowMs = currentPolicy().getNotifyAggregateMs();
                long ticks = Math.max(1, windowMs / 50);
                scheduler.runLater(() -> flushTail(key), ticks);
                return created;
            }
            existing.count++;
            return existing;
        });
    }

    /** 窗口尾部冲刷：批次内事件统一补发一条（多事件带 ×N，单发不带次数）。 */
    private void flushTail(String key) {
        PendingAlert alert = pendingAlerts.remove(key);
        if (alert == null) {
            return;
        }
        String suffix = alert.count > 1 ? " ×" + alert.count : "";
        notifyAggregated(alert.epicenter, alert.message + suffix, alert.blockType, alert.explosionPrefix);
    }

    /** 渲染并派发 tnt_alert：游戏内广播 + 群消息走同一条聚合结果。 */
    private void notifyAggregated(Location location, String message, String blockType, boolean explosionPrefix) {
        TemplateOptions opt = configs.templateOptions();
        java.util.Map<String, String> vars = CoordFormatter.format(location, opt);
        vars.put("msg", message);
        vars.put("actor", "");
        vars.put("block_type", blockType);
        MessageEnvelope envelope = configs.renderEvent("tnt_alert", vars);
        TextComponent msg = Component.text()
                .append(explosionPrefix ? styles.explosionPrefix() : styles.tntPrefix())
                .append(Component.text(envelope.message()))
                .build();
        notifier.server(msg);
        notifier.event("tnt_alert", envelope);
    }

    private void sendPlacementNotification(Player player, Block block) {
        TextComponent msg = Component.text()
                .append(playerInfo(player))
                .append(Component.space())
                .append(Component.text("在"))
                .append(locationComponent(block))
                .append(Component.space())
                .append(Component.text("放置了 " + "TNT"))
                .build();
        notifier.server(msg);
        TemplateOptions opt = configs.templateOptions();
        java.util.Map<String, String> vars = CoordFormatter.format(block.getLocation(), opt);
        vars.put("msg", "放置TNT");
        vars.put("actor", PlayerDisplayNames.format(player));
        vars.put("block_type", "TNT");
        MessageEnvelope envelope = configs.renderEvent("tnt_alert", vars);
        notifier.event("tnt_alert", envelope);
    }

    private @NotNull TextComponent playerInfo(@Nullable Player player) {
        if (player != null) {
            return styles.playerName(player.getName());
        }
        return styles.unknownLabel();
    }

    private @NotNull TextComponent locationComponent(@NotNull Block block) {
        return locationComponent(block.getLocation());
    }

    private @NotNull TextComponent locationComponent(Location location) {
        String locString = locationString(location);
        return styles.coordComponent(locString);
    }

    private @NotNull String locationString(@NotNull Location location) {
        return styles.coordString(location);
    }

    /** 聚合 key：世界 + 128×128×64 方块区域 + 消息类型，保证批次内事件地理上相邻（含高度）、消息类型一致。 */
    private @NotNull String aggregateKey(@NotNull Location location, @NotNull String message) {
        int rx = Math.floorDiv(location.getBlockX(), REGION_SIZE_BLOCKS);
        int rz = Math.floorDiv(location.getBlockZ(), REGION_SIZE_BLOCKS);
        int ry = Math.floorDiv(location.getBlockY(), REGION_VERTICAL_BLOCKS);
        String world = location.getWorld().getName();
        return world + "|" + rx + "|" + ry + "|" + rz + "|" + message;
    }

    /** 一批待聚合的 TNT/爆炸告警。字段仅在 {@code pendingAlerts.compute} 的映射函数内读写（按 key 串行）。 */
    private static final class PendingAlert {
        int count;

        @NotNull
        Location epicenter;

        @NotNull
        String message;

        @NotNull
        String blockType;

        boolean explosionPrefix;
    }

    private boolean isExemptEntity(@NotNull EntityType type) {
        EnumSet<EntityType> exempt = buildExemptTypes(configs.tnt());
        return exempt.contains(type);
    }

    private static EnumSet<EntityType> buildExemptTypes(TntConfig cfg) {
        EnumSet<EntityType> set = EnumSet.noneOf(EntityType.class);
        List<String> names = cfg.exemptEntities();
        if (names.isEmpty()) {
            names = DEFAULT_EXEMPT_ENTITIES;
        }
        for (String name : names) {
            try {
                set.add(EntityType.valueOf(name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return set;
    }
}
