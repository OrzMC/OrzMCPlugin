package com.jokerhub.paper.plugin.orzmc.infra.portal;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalInfo;
import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalPort;
import com.jokerhub.paper.plugin.orzmc.core.ports.portal.WorldProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.portal.PortalBuilder.PortalBuildResult;
import com.jokerhub.paper.plugin.orzmc.infra.server.RegionSchedulerProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * 传送门服务。
 *
 * <p>协调传送门的构建、查找、拆除和持久化。
 * 实际工作委派给 {@link PortalBuilder}（方块构建）、{@link PortalLabelRenderer}（标签渲染）、
 * {@link PortalCleaner}（方块清理）和 {@link PortalPersistence}（数据持久化）。</p>
 *
 * <p>Folia：createPortal 由玩家命令触发（玩家 region 线程），方块构建直接执行；
 * 标签渲染/方块清理经 {@link RegionSchedulerProvider} 投递到目标 chunk 的 region 线程。
 * {@code interiorTargets}/{@code portalCenters} 在 Folia 下会被不同 region 并发读写，故用线程安全 Map。</p>
 */
public class PortalService implements PortalPort {

    private final ConfigService configService;
    private final Logger logger;
    private final PortalBuilder portalBuilder;
    private final PortalLabelRenderer labelRenderer;
    private final PortalCleaner portalCleaner;
    private final PortalPersistence persistence;

    private final Map<String, String> interiorTargets = new ConcurrentHashMap<>();
    private final Map<String, PortalDef> portalCenters = new ConcurrentHashMap<>();

    /** 测试兜底构造：同步直跑（不投递 region）。生产使用注入 {@link RegionSchedulerProvider} 的构造。 */
    public PortalService(ConfigService configService) {
        this(configService, RegionSchedulerProvider.inline());
    }

    /** 生产入口：方块/标签操作经 region scheduler 投递到目标 chunk 的 region 线程。 */
    public PortalService(ConfigService configService, RegionSchedulerProvider regionScheduler) {
        this(configService, new BukkitWorldProvider(), regionScheduler);
    }

    public PortalService(ConfigService configService, WorldProvider worldProvider) {
        this(configService, worldProvider, RegionSchedulerProvider.inline());
    }

    public PortalService(
            ConfigService configService, WorldProvider worldProvider, RegionSchedulerProvider regionScheduler) {
        this.configService = configService;
        this.logger = Logger.getLogger("PortalService");
        this.portalBuilder = new PortalBuilder(interiorTargets, logger);
        this.labelRenderer = new PortalLabelRenderer(worldProvider, logger, regionScheduler);
        this.portalCleaner = new PortalCleaner(worldProvider, logger, regionScheduler);
        this.persistence = new PortalPersistence(configService, logger);
    }

    /** 传送门定义记录。 */
    public record PortalDef(String world, int cx, int cy, int cz, Axis axis, String target) {
        public String centerKey() {
            return world + ":" + cx + ":" + cy + ":" + cz;
        }
    }

    // ---- PortalPort implementation ----

    @Override
    public PortalInfo createPortal(Player player, String host, int port) {
        String target = host + ":" + port;
        PortalBuildResult result = portalBuilder.build(player, target);
        PortalDef def =
                new PortalDef(result.worldName(), result.cx(), result.cy(), result.cz(), result.portalAxis(), target);
        portalCenters.put(def.centerKey(), def);
        rehydrateInterior(def);
        labelRenderer.spawnLabel(def.world(), def.cx(), def.cy(), def.cz(), def.target());
        persistence.save(portalCenters);
        return new PortalInfo(result.center(), result.infoAxis());
    }

    @Override
    public String findTarget(Location location) {
        // 先精确命中，再做 3×3×3 邻域容差（Paper 路径玩家站在门内时方块坐标可能有 1 格对齐偏差）
        String v = findTargetExact(location);
        if (v != null) return v;
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        World w = location.getWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String kk = key(w, bx + dx, by + dy, bz + dz);
                    String vv = interiorTargets.get(kk);
                    if (vv != null) return vv;
                }
            }
        }
        return null;
    }

    @Override
    public String findTargetExact(Location location) {
        String k = key(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return interiorTargets.getOrDefault(k, null);
    }

    @Override
    public int removeByTarget(String target) {
        List<PortalDef> toRemove = new ArrayList<>();
        for (PortalDef def : portalCenters.values()) {
            if (target.equals(def.target())) {
                toRemove.add(def);
            }
        }
        for (PortalDef def : toRemove) {
            portalCleaner.clear(def);
            portalCenters.remove(def.centerKey());
            clearInterior(def);
            labelRenderer.clearLabels(def.world(), def.cx(), def.cy(), def.cz(), def.target());
        }
        persistence.save(portalCenters);
        configService.reloadConfig("portals");
        return toRemove.size();
    }

    // ---- Lifecycle (not part of PortalPort contract) ----

    public void setup() {
        persistence.load(portalCenters, this::rehydrateInterior);
        for (PortalDef def : portalCenters.values()) {
            labelRenderer.spawnLabel(def.world(), def.cx(), def.cy(), def.cz(), def.target());
        }
    }

    public void tearDown() {}

    // ---- Internal helpers ----

    private String key(World w, int x, int y, int z) {
        return w.getName() + ":" + x + ":" + y + ":" + z;
    }

    private void rehydrateInterior(PortalDef def) {
        if (def.axis == Axis.X) {
            int z = def.cz;
            int x1 = def.cx;
            int x2 = def.cx + 1;
            int y1 = def.cy - 1;
            int y2 = def.cy;
            int y3 = def.cy + 1;
            interiorTargets.put(def.world + ":" + x1 + ":" + y1 + ":" + z, def.target);
            interiorTargets.put(def.world + ":" + x1 + ":" + y2 + ":" + z, def.target);
            interiorTargets.put(def.world + ":" + x1 + ":" + y3 + ":" + z, def.target);
            interiorTargets.put(def.world + ":" + x2 + ":" + y1 + ":" + z, def.target);
            interiorTargets.put(def.world + ":" + x2 + ":" + y2 + ":" + z, def.target);
            interiorTargets.put(def.world + ":" + x2 + ":" + y3 + ":" + z, def.target);
        } else {
            int x = def.cx;
            int z1 = def.cz;
            int z2 = def.cz + 1;
            int y1 = def.cy - 1;
            int y2 = def.cy;
            int y3 = def.cy + 1;
            interiorTargets.put(def.world + ":" + x + ":" + y1 + ":" + z1, def.target);
            interiorTargets.put(def.world + ":" + x + ":" + y2 + ":" + z1, def.target);
            interiorTargets.put(def.world + ":" + x + ":" + y3 + ":" + z1, def.target);
            interiorTargets.put(def.world + ":" + x + ":" + y1 + ":" + z2, def.target);
            interiorTargets.put(def.world + ":" + x + ":" + y2 + ":" + z2, def.target);
            interiorTargets.put(def.world + ":" + x + ":" + y3 + ":" + z2, def.target);
        }
    }

    private void clearInterior(PortalDef def) {
        int z = def.cz;
        int x1 = def.cx;
        int x2 = def.cx + 1;
        int y1 = def.cy - 1;
        int y2 = def.cy;
        int y3 = def.cy + 1;
        interiorTargets.remove(def.world + ":" + x1 + ":" + y1 + ":" + z);
        interiorTargets.remove(def.world + ":" + x1 + ":" + y2 + ":" + z);
        interiorTargets.remove(def.world + ":" + x1 + ":" + y3 + ":" + z);
        interiorTargets.remove(def.world + ":" + x2 + ":" + y1 + ":" + z);
        interiorTargets.remove(def.world + ":" + x2 + ":" + y2 + ":" + z);
        interiorTargets.remove(def.world + ":" + x2 + ":" + y3 + ":" + z);
    }
}
