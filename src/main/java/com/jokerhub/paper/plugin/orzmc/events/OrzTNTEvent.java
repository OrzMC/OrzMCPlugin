package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.tnt.TntPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateRenderer;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateResolvers;
import com.jokerhub.paper.plugin.orzmc.utils.OrzMessageParser;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrzTNTEvent extends OrzBaseListener {
    private final TntPolicy policy;
    // 冷却时间跟踪
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final EnumSet<EntityType> explosionExemptTypes = EnumSet.noneOf(EntityType.class);

    public OrzTNTEvent(OrzMC plugin) {
        super(plugin);
        FileConfiguration tntConfig = plugin.configManager.getConfig("tnt");
        TypedConfigs.TntConfig typed = TypedConfigs.TntConfig.from(tntConfig);
        this.policy = new TntPolicy(typed);
        initExplosionExemptTypes(tntConfig);
    }

    @EventHandler
    public void onTNTPrime(@NotNull TNTPrimeEvent event) {
        Block placedBlock = event.getBlock();

        // 如果全局禁用TNT且不在白名单区域，取消事件
        if (!policy.isEnableTnt() && policy.isNotInWhiteList(placedBlock.getLocation())) {
            event.setCancelled(true);
            notifyTNTEvent(placedBlock, "TNT被点燃（已禁止）");
            return;
        }

        // 即使允许，也记录TNT点燃事件
        notifyTNTEvent(placedBlock, "TNT被点燃");
    }

    @EventHandler
    public void onPlaceBlock(@NotNull BlockPlaceEvent event) {
        Block placedBlock = event.getBlockPlaced();
        Material placedBlockType = placedBlock.getType();
        Player player = event.getPlayer();

        // 处理TNT放置
        if (placedBlockType == Material.TNT) {
            handleTNTPlace(event, player, placedBlock);
            return;
        }

        // 处理重生锚放置
        if (placedBlockType == Material.RESPAWN_ANCHOR && !policy.isEnableRespawnAnchor()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("重生锚放置已被管理员禁用").color(TextColor.color(0xFF5555)));
        }
    }

    private void handleTNTPlace(BlockPlaceEvent event, Player player, Block placedBlock) {
        // 检查冷却时间
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

        // 如果全局禁用TNT且不在白名单区域，取消放置
        if (!policy.isEnableTnt() && policy.isNotInWhiteList(placedBlock.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("TNT放置已被管理员禁用").color(TextColor.color(0xFF5555)));
            return;
        }

        // 记录冷却时间
        if (tntPlaceCooldown > 0) {
            playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }

        // 发送放置通知
        sendPlacementNotification(player, placedBlock);
    }

    private boolean checkCooldown(@NotNull Player player, int tntPlaceCooldown) {
        if (!playerCooldowns.containsKey(player.getUniqueId())) {
            return false;
        }
        long lastPlaceTime = playerCooldowns.get(player.getUniqueId());
        return System.currentTimeMillis() - lastPlaceTime < tntPlaceCooldown * 1000L;
    }

    @EventHandler
    public void onBlockPreDispense(@NotNull BlockPreDispenseEvent event) {
        ItemStack itemStack = event.getItemStack();
        Material itemType = itemStack.getType();

        // 只处理TNT相关物品
        if (itemType != Material.TNT && itemType != Material.TNT_MINECART) {
            return;
        }

        Block dispenser = event.getBlock();

        // 如果全局禁用TNT且不在白名单区域，取消发射
        if (!policy.isEnableTnt() && policy.isNotInWhiteList(dispenser.getLocation())) {
            event.setCancelled(true);
            notifyTNTEvent(dispenser, "发射" + itemType.name() + "被禁止");
        }
    }

    @EventHandler
    public void onBlockExplode(@NotNull BlockExplodeEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        if (material.isAir()) {
            return;
        }
        Location loc = block.getLocation();
        String key = explosionKey(loc, material.name() + "爆炸");
        ThrottledNotifier.runDefault(key, () -> notifyExplosionEvent(loc, material.name() + "爆炸"));
    }

    @EventHandler
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        EntityType entityType = event.getEntityType();
        if (explosionExemptTypes.contains(entityType)) {
            return;
        }
        Location loc = event.getLocation();
        String key = explosionKey(loc, entityType.name() + "爆炸");
        ThrottledNotifier.runDefault(key, () -> notifyExplosionEvent(loc, entityType.name() + "爆炸"));
    }

    // 区域检查由 policy 处理

    // 通知方法
    private void notifyTNTEvent(Block block, String message) {
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        org.bukkit.Location loc = block.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        TypedConfigs.TemplateOptions opt =
                TypedConfigs.TemplateOptions.from(plugin.configManager.getConfig("templates"));
        String worldAlias = TemplateResolvers.worldAlias(
                world, loc.getWorld() != null ? loc.getWorld().getEnvironment().name() : "", opt);
        double scale = opt.coordScale() <= 0 ? 1.0 : opt.coordScale();
        int precision = OrzMC.plugin().configManager.getConfig("templates").getInt("templates.coord.precision", 2);
        if (precision < 0) precision = 2;
        String fmt = "%." + precision + "f";
        String xUnit = String.format(fmt, loc.getBlockX() * scale);
        String yUnit = String.format(fmt, loc.getBlockY() * scale);
        String zUnit = String.format(fmt, loc.getBlockZ() * scale);
        vars.put("world", worldAlias);
        vars.put("x", String.valueOf(loc.getBlockX()));
        vars.put("y", String.valueOf(loc.getBlockY()));
        vars.put("z", String.valueOf(loc.getBlockZ()));
        vars.put("x_unit", xUnit);
        vars.put("y_unit", yUnit);
        vars.put("z_unit", zUnit);
        vars.put("coord_unit", opt.coordUnitLabel());
        vars.put("msg", message);
        vars.put("actor", "");
        vars.put("block_type", block.getType().name());
        String rendered = TemplateRenderer.render(
                TypedConfigs.Templates.from(plugin.configManager.getConfig("templates"))
                        .tntAlert(),
                vars);
        TextComponent msg = Component.text()
                .append(OrzTextStyles.tntPrefix())
                .append(Component.text(rendered))
                .build();

        Notifier.server(msg);
        Notifier.event("tnt_alert", rendered);
    }

    private void notifyExplosionEvent(Location location, String message) {
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        String world = location.getWorld() != null ? location.getWorld().getName() : "unknown";
        TypedConfigs.TemplateOptions opt =
                TypedConfigs.TemplateOptions.from(plugin.configManager.getConfig("templates"));
        String worldAlias = TemplateResolvers.worldAlias(
                world,
                location.getWorld() != null
                        ? location.getWorld().getEnvironment().name()
                        : "",
                opt);
        double scale = opt.coordScale() <= 0 ? 1.0 : opt.coordScale();
        int precision = OrzMC.plugin().configManager.getConfig("templates").getInt("templates.coord.precision", 2);
        if (precision < 0) precision = 2;
        String fmt = "%." + precision + "f";
        String xUnit = String.format(fmt, location.getBlockX() * scale);
        String yUnit = String.format(fmt, location.getBlockY() * scale);
        String zUnit = String.format(fmt, location.getBlockZ() * scale);
        vars.put("world", worldAlias);
        vars.put("x", String.valueOf(location.getBlockX()));
        vars.put("y", String.valueOf(location.getBlockY()));
        vars.put("z", String.valueOf(location.getBlockZ()));
        vars.put("x_unit", xUnit);
        vars.put("y_unit", yUnit);
        vars.put("z_unit", zUnit);
        vars.put("coord_unit", opt.coordUnitLabel());
        vars.put("msg", message);
        vars.put("actor", "");
        vars.put("block_type", "EXPLOSION");
        String rendered = TemplateRenderer.render(
                TypedConfigs.Templates.from(plugin.configManager.getConfig("templates"))
                        .tntAlert(),
                vars);
        TextComponent msg = Component.text()
                .append(OrzTextStyles.explosionPrefix())
                .append(Component.text(rendered))
                .build();

        Notifier.server(msg);
        Notifier.event("tnt_alert", rendered);
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

        Notifier.server(msg);
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        org.bukkit.Location loc = block.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        TypedConfigs.TemplateOptions opt =
                TypedConfigs.TemplateOptions.from(plugin.configManager.getConfig("templates"));
        String worldAlias = TemplateResolvers.worldAlias(
                world, loc.getWorld() != null ? loc.getWorld().getEnvironment().name() : "", opt);
        double scale = opt.coordScale() <= 0 ? 1.0 : opt.coordScale();
        int precision = OrzMC.plugin().configManager.getConfig("templates").getInt("templates.coord.precision", 2);
        if (precision < 0) precision = 2;
        String fmt = "%." + precision + "f";
        String xUnit = String.format(fmt, loc.getBlockX() * scale);
        String yUnit = String.format(fmt, loc.getBlockY() * scale);
        String zUnit = String.format(fmt, loc.getBlockZ() * scale);
        vars.put("world", worldAlias);
        vars.put("x", String.valueOf(loc.getBlockX()));
        vars.put("y", String.valueOf(loc.getBlockY()));
        vars.put("z", String.valueOf(loc.getBlockZ()));
        vars.put("x_unit", xUnit);
        vars.put("y_unit", yUnit);
        vars.put("z_unit", zUnit);
        vars.put("coord_unit", opt.coordUnitLabel());
        vars.put("msg", "放置TNT");
        vars.put("actor", OrzMessageParser.playerDisplayName(player));
        vars.put("block_type", "TNT");
        String rendered = TemplateRenderer.render(
                TypedConfigs.Templates.from(plugin.configManager.getConfig("templates"))
                        .tntAlert(),
                vars);
        Notifier.event("tnt_alert", rendered);
    }

    // 信息构建工具
    private @NotNull TextComponent playerInfo(@Nullable Player player) {
        if (player != null) {
            return OrzTextStyles.playerName(player.getName());
        } else {
            return OrzTextStyles.unknownLabel();
        }
    }

    private @NotNull TextComponent locationComponent(@NotNull Block block) {
        return locationComponent(block.getLocation());
    }

    private @NotNull TextComponent locationComponent(Location location) {
        String locString = locationString(location);
        return OrzTextStyles.coordComponent(locString);
    }

    private @NotNull String locationString(@NotNull Location location) {
        return OrzTextStyles.coordString(location);
    }

    private @NotNull String explosionKey(@NotNull Location location, @NotNull String message) {
        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        String world = location.getWorld().getName();
        return world + "|" + cx + "|" + cz + "|" + message;
    }

    // 区域内部类移至 TntPolicy

    private void addExemptTypeIfAvailable(String name) {
        try {
            explosionExemptTypes.add(EntityType.valueOf(name));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void initExplosionExemptTypes(@NotNull FileConfiguration tntConfig) {
        List<String> names = tntConfig.getStringList("exempt_entities");
        if (names.isEmpty()) {
            names = List.of(
                    "CREEPER",
                    "FIREBALL",
                    "WIND_CHARGE",
                    "BREEZE_WIND_CHARGE",
                    "ENDER_DRAGON",
                    "END_CRYSTAL",
                    "WITHER",
                    "WITHER_SKULL");
        }
        names.forEach(this::addExemptTypeIfAvailable);
    }
}
