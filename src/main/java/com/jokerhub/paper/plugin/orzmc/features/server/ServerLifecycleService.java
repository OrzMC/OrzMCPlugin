package com.jokerhub.paper.plugin.orzmc.features.server;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateService;
import org.bukkit.configuration.file.FileConfiguration;

public final class ServerLifecycleService {
    private final ConfigService configService;
    private final Notifier notifier;

    public ServerLifecycleService(ConfigService configService, Notifier notifier) {
        this.configService = configService;
        this.notifier = notifier;
    }

    public void notifyServerStop() {
        String minecraftVersion = OrzMC.server().getMinecraftVersion();
        String msg = "Minecraft " + minecraftVersion + "\n" + "------" + "\n" + "服务停止" + "\n\n" + "停止状态无法响应命令消息";
        FileConfiguration templatesCfg = configService.getConfig("templates");
        TypedConfigs.Templates tpls = TypedConfigs.Templates.from(templatesCfg);
        MessageEnvelope env =
                TemplateService.renderEvent("server_stop", templatesCfg, tpls, java.util.Map.of("message", msg));
        notifier.event("server_stop", env);
    }
}
