package com.jokerhub.paper.plugin.orzmc.infra.portal;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.WorldProvider;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * 基于 {@link Bukkit#getWorld} 的 {@link WorldProvider} 实现。
 */
public final class BukkitWorldProvider implements WorldProvider {

    @Override
    public World getWorld(String name) {
        return Bukkit.getWorld(name);
    }
}
