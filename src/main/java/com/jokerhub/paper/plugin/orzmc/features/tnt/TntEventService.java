package com.jokerhub.paper.plugin.orzmc.features.tnt;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.templates.CoordFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.player.PlayerDisplayNames;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
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
    private final TypedConfigProvider configs;
    private final TntPolicy policy;
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final EnumSet<EntityType> explosionExemptTypes = EnumSet.noneOf(EntityType.class);
    private final OrzTextStyles styles;
    private final Notifier notifier;
    private final ThrottledNotifier throttledNotifier;

    public TntEventService(
            TypedConfigProvider configs, OrzTextStyles styles, Notifier notifier, ThrottledNotifier throttledNotifier) {
        this.configs = configs;
        this.styles = styles;
        this.notifier = notifier;
        this.throttledNotifier = throttledNotifier;
        TntConfig typed = configs.tnt();
        this.policy = new TntPolicy(typed);
        initExplosionExemptTypes(typed);
    }

    public void onTNTPrime(@NotNull TNTPrimeEvent event) {
        Block placedBlock = event.getBlock();
        if (!policy.isEnableTnt() && policy.isNotInWhiteList(placedBlock.getLocation())) {
            event.setCancelled(true);
            notifyTNTEvent(placedBlock, "TNT被点燃（已禁止）");
            return;
        }
        notifyTNTEvent(placedBlock, "TNT被点燃");
    }

    public void onPlaceBlock(@NotNull BlockPlaceEvent event) {
        Block placedBlock = event.getBlockPlaced();
        Material placedBlockType = placedBlock.getType();
        Player player = event.getPlayer();
        if (placedBlockType == Material.TNT) {
            handleTNTPlace(event, player, placedBlock);
            return;
        }
        if (placedBlockType == Material.RESPAWN_ANCHOR && !policy.isEnableRespawnAnchor()) {
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
        if (!policy.isEnableTnt() && policy.isNotInWhiteList(dispenser.getLocation())) {
            event.setCancelled(true);
            notifyTNTEvent(dispenser, "发射" + itemType.name() + "被禁止");
        }
    }

    public void onBlockExplode(@NotNull BlockExplodeEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        if (material.isAir()) {
            return;
        }
        Location loc = block.getLocation();
        String key = explosionKey(loc, material.name() + "爆炸");
        throttledNotifier.runDefault(key, () -> notifyExplosionEvent(loc, material.name() + "爆炸"));
    }

    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        EntityType entityType = event.getEntityType();
        if (explosionExemptTypes.contains(entityType)) {
            return;
        }
        Location loc = event.getLocation();
        String key = explosionKey(loc, entityType.name() + "爆炸");
        throttledNotifier.runDefault(key, () -> notifyExplosionEvent(loc, entityType.name() + "爆炸"));
    }

    private void handleTNTPlace(BlockPlaceEvent event, Player player, Block placedBlock) {
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

    private void notifyTNTEvent(Block block, String message) {
        TemplateOptions opt = configs.templateOptions();
        java.util.Map<String, String> vars = CoordFormatter.format(block.getLocation(), opt);
        vars.put("msg", message);
        vars.put("actor", "");
        vars.put("block_type", block.getType().name());
        MessageEnvelope envelope = configs.renderEvent("tnt_alert", vars);
        TextComponent msg = Component.text()
                .append(styles.tntPrefix())
                .append(Component.text(envelope.message()))
                .build();
        notifier.server(msg);
        notifier.event("tnt_alert", envelope);
    }

    private void notifyExplosionEvent(Location location, String message) {
        TemplateOptions opt = configs.templateOptions();
        java.util.Map<String, String> vars = CoordFormatter.format(location, opt);
        vars.put("msg", message);
        vars.put("actor", "");
        vars.put("block_type", "EXPLOSION");
        MessageEnvelope envelope = configs.renderEvent("tnt_alert", vars);
        TextComponent msg = Component.text()
                .append(styles.explosionPrefix())
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

    private @NotNull String explosionKey(@NotNull Location location, @NotNull String message) {
        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        String world = location.getWorld().getName();
        return world + "|" + cx + "|" + cz + "|" + message;
    }

    private void addExemptTypeIfAvailable(String name) {
        try {
            explosionExemptTypes.add(EntityType.valueOf(name));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void initExplosionExemptTypes(@NotNull TntConfig tntConfig) {
        List<String> names = tntConfig.exemptEntities();
        if (names.isEmpty()) {
            names = List.of(
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
        }
        names.forEach(this::addExemptTypeIfAvailable);
    }
}
