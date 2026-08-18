package com.jokerhub.paper.plugin.orzmc.infra.server;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/** 基于 {@link Bukkit#getRegionScheduler()} 的真实投递实现（Paper 上为主线程执行，Folia 上为 chunk 所属 region 线程）。 */
public final class BukkitRegionSchedulerProvider implements RegionSchedulerProvider {

    private final JavaPlugin plugin;

    public BukkitRegionSchedulerProvider(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run(World world, int chunkX, int chunkZ, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, task);
    }
}
