package com.jokerhub.paper.plugin.orzmc;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.VisibleForTesting;

public class OrzMC extends JavaPlugin {
    private OrzServices services;

    @Override
    public void onEnable() {
        getLogger().info("插件生效!");
        services = OrzServices.assemble(this);
        services.setupAll(this);
    }

    @Override
    public void onDisable() {
        services.shutdownAll();
        getLogger().info("插件失效!");
    }

    @VisibleForTesting
    public OrzServices services() {
        return services;
    }
}
