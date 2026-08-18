package com.jokerhub.paper.plugin.orzmc.infra.server;

import static org.mockito.Mockito.*;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BukkitRegionSchedulerProviderTest {

    @Test
    void run_delegatesToBukkitRegionScheduler() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        World world = mock(World.class);
        Runnable task = mock(Runnable.class);
        RegionScheduler regionScheduler = mock(RegionScheduler.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(regionScheduler);

            new BukkitRegionSchedulerProvider(plugin).run(world, 3, 4, task);

            verify(regionScheduler).execute(plugin, world, 3, 4, task);
        }
    }
}
