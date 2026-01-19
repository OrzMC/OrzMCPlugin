package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.utils.OrzMessageParser;
import io.papermc.paper.event.block.BlockPreDispenseEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OrzTNTEvent extends OrzBaseListener {
    // 白名单区域内，可以允许 TNT
    private final List<Region> whiteListRegions = new ArrayList<>();
    private final boolean enableTNT;
    private final boolean enableRespawnAnchor;
    private final int tntPlaceCooldown; // 秒
    // 冷却时间跟踪
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    public OrzTNTEvent(OrzMC plugin) {
        super(plugin);
        FileConfiguration tntConfig = plugin.configManager.getConfig("tnt");
        this.enableTNT = tntConfig.getBoolean("enable", false);
        this.enableRespawnAnchor = tntConfig.getBoolean("enable_respawn_anchor", false);
        this.tntPlaceCooldown = tntConfig.getInt("place_cooldown", 0);

        // 加载TNT放置区域白名单
        List<Map<?, ?>> regions = tntConfig.getMapList("whitelist");
        for (Map<?, ?> regionMap : regions) {
            Region region = new Region(
                    ((Number) regionMap.get("minX")).intValue(),
                    ((Number) regionMap.get("maxX")).intValue(),
                    ((Number) regionMap.get("minY")).intValue(),
                    ((Number) regionMap.get("maxY")).intValue(),
                    ((Number) regionMap.get("minZ")).intValue(),
                    ((Number) regionMap.get("maxZ")).intValue(),
                    (String) regionMap.get("world")
            );
            this.whiteListRegions.add(region);
        }
    }

    @EventHandler
    public void onTNTPrime(@NotNull TNTPrimeEvent event) {
        Block placedBlock = event.getBlock();

        // 如果全局禁用TNT且不在白名单区域，取消事件
        if (!this.enableTNT && isNotInWhiteList(placedBlock)) {
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
        if (placedBlockType == Material.RESPAWN_ANCHOR && !enableRespawnAnchor) {
            event.setCancelled(true);
            player.sendMessage(Component.text("重生锚放置已被管理员禁用").color(TextColor.color(0xFF5555)));
        }
    }

    private void handleTNTPlace(BlockPlaceEvent event, Player player, Block placedBlock) {
        // 检查冷却时间
        if (tntPlaceCooldown > 0 && checkCooldown(player)) {
            event.setCancelled(true);
            long remaining = (playerCooldowns.get(player.getUniqueId()) + tntPlaceCooldown * 1000L - System.currentTimeMillis()) / 1000;
            player.sendMessage(Component.text()
                    .append(Component.text("放置TNT冷却中，请等待 "))
                    .append(Component.text(remaining + "秒").color(TextColor.color(0xFFAA00)))
                    .build());
            return;
        }

        // 如果全局禁用TNT且不在白名单区域，取消放置
        if (!enableTNT && isNotInWhiteList(placedBlock)) {
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

    private boolean checkCooldown(@NotNull Player player) {
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
        if (!enableTNT && isNotInWhiteList(dispenser)) {
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
        notifyExplosionEvent(block.getLocation(), material.name() + "爆炸");
    }
    @EventHandler
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        EntityType entityType = event.getEntityType();
        if (entityType == EntityType.CREEPER) {
            return;
        }
        String typeName = entityType.name();
        if ("BREEZE_WIND_CHARGE".equals(typeName) || "WIND_CHARGE".equals(typeName)) {
            return;
        }
        if (entityType == EntityType.FIREBALL) {
            return;
        }
        notifyExplosionEvent(event.getLocation(), entityType.name() + "爆炸");
    }

    // 区域检查方法
    private boolean isNotInWhiteList(@NotNull Block block) {
        Location loc = block.getLocation();
        for (Region region : whiteListRegions) {
            if (region.contains(loc)) {
                return false;
            }
        }
        return true;
    }

    // 通知方法
    private void notifyTNTEvent(Block block, String message) {
        TextComponent msg = Component.text()
                .append(Component.text("[TNT警报] ").color(TextColor.color(0xFF5555)))
                .append(playerInfo(null)) // 尝试获取放置玩家
                .append(Component.space())
                .append(locationComponent(block))
                .append(Component.space())
                .append(Component.text(message))
                .build();

        plugin.getServer().sendMessage(msg);
        plugin.sendPublicMessage("[TNT警报] " + locationString(block) + message);
    }

    private void notifyExplosionEvent(Location location, String message) {
        TextComponent msg = Component.text()
                .append(Component.text("[爆炸警报] ").color(TextColor.color(0xFFAA00)))
                .append(locationComponent(location))
                .append(Component.space())
                .append(Component.text(message))
                .build();

        plugin.getServer().sendMessage(msg);
        plugin.sendPublicMessage("[爆炸警报] " + locationString(location) + message);
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

        plugin.getServer().sendMessage(msg);
        plugin.sendPublicMessage(OrzMessageParser.playerDisplayName(player) +
                " 在" + locationString(block) + "放置了 " + "TNT");
    }

    // 信息构建工具
    private @NotNull TextComponent playerInfo(@Nullable Player player) {
        if (player != null) {
            return Component.text(player.getName())
                    .color(TextColor.color(0xFF5555));
        } else {
            return Component.text("未知玩家").color(TextColor.color(0xAAAAAA));
        }
    }

    private @NotNull TextComponent locationComponent(@NotNull Block block) {
        return locationComponent(block.getLocation());
    }

    private @NotNull TextComponent locationComponent(Location location) {
        String locString = locationString(location);
        return Component.text(locString)
                .color(TextColor.color(0x55FF55))
                .hoverEvent(HoverEvent.showText(Component.text("点击复制坐标")))
                .clickEvent(ClickEvent.copyToClipboard(locString.trim()));
    }

    private @NotNull String locationString(@NotNull Block block) {
        return locationString(block.getLocation());
    }

    private @NotNull String locationString(@NotNull Location location) {
        return String.format(" [%s] %d %d %d ",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    // 区域内部类
    private record Region(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, String world) {
        private Region(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, String world) {
            this.minX = Math.min(minX, maxX);
            this.maxX = Math.max(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.maxY = Math.max(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxZ = Math.max(minZ, maxZ);
            this.world = world;
        }

        public boolean contains(@NotNull Location loc) {
            return loc.getWorld().getName().equals(world) &&
                    loc.getX() >= minX && loc.getX() <= maxX &&
                    loc.getY() >= minY && loc.getY() <= maxY &&
                    loc.getZ() >= minZ && loc.getZ() <= maxZ;
        }
    }
}
