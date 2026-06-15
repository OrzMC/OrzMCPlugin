package com.jokerhub.paper.plugin.orzmc.infra.portal;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.PortalsWriter;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Portals;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Portals.PortalEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Axis;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 传送门持久化。
 *
 * <p>负责从 YAML 文件加载和保存传送门数据。</p>
 */
public final class PortalPersistence {

    private final ConfigService configService;
    private final Logger logger;

    public PortalPersistence(ConfigService configService, Logger logger) {
        this.configService = configService;
        this.logger = logger;
    }

    /**
     * 从 portals.yml 加载所有传送门定义。
     *
     * @param portalCenters 传入的空 Map，加载的数据将写入此 Map
     * @param rehydrateFn   每个 PortalDef 的回调，用于重建内存索引（interior targets 等）
     */
    public void load(
            Map<String, PortalService.PortalDef> portalCenters,
            java.util.function.Consumer<PortalService.PortalDef> rehydrateFn) {
        FileConfiguration cfg = configService.getConfig("portals");
        Portals typed = Portals.from(cfg);
        for (Map.Entry<String, PortalEntry> e : typed.entries().entrySet()) {
            String k = e.getKey();
            String[] parts = k.split(":");
            if (parts.length != 4) continue;
            String world = parts[0];
            int cx, cy, cz;
            try {
                cx = Integer.parseInt(parts[1]);
                cy = Integer.parseInt(parts[2]);
                cz = Integer.parseInt(parts[3]);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "PortalService 读取配置坐标失败，跳过: " + k, ex);
                continue;
            }
            String axisStr = e.getValue().axis();
            String target = e.getValue().target();
            if (target == null || target.isEmpty()) continue;
            Axis axis = "Z".equalsIgnoreCase(axisStr) ? Axis.Z : Axis.X;
            PortalService.PortalDef def = new PortalService.PortalDef(world, cx, cy, cz, axis, target);
            portalCenters.put(def.centerKey(), def);
            rehydrateFn.accept(def);
        }
    }

    /**
     * 保存当前所有传送门到 portals.yml。
     */
    public void save(Map<String, PortalService.PortalDef> portalCenters) {
        FileConfiguration cfg = configService.getConfig("portals");
        if (cfg == null) return;
        Map<String, PortalEntry> entries = new HashMap<>();
        for (PortalService.PortalDef def : portalCenters.values()) {
            entries.put(def.centerKey(), new PortalEntry(def.target(), def.axis() == Axis.Z ? "Z" : "X"));
        }
        PortalsWriter.write(cfg, entries);
        configService.saveConfig("portals");
    }
}
