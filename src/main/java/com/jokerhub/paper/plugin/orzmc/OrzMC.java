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
        // assemble() 抛异常时 services 保持 null，onDisable 仍会被 Bukkit 回调——判空避免掩盖启动错误
        if (services != null) {
            services.shutdownAll();
        }
        getLogger().info("插件失效!");
    }

    @VisibleForTesting
    public OrzServices services() {
        return services;
    }
}
