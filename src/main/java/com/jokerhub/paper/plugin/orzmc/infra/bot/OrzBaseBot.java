package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import org.bukkit.configuration.file.FileConfiguration;

public abstract class OrzBaseBot {
    public abstract boolean isEnable();

    public abstract void setup();

    public abstract void teardown();

    public abstract void sendMessage(String message);

    public abstract void sendPrivateMessage(String message);

    protected final OrzMC plugin;
    protected final FileConfiguration botConfig;

    protected OrzBaseBot(OrzMC plugin) {
        this.plugin = plugin;
        botConfig = plugin.configManager.getConfig("bot");
    }

    public void sendToChannel(String channelKey, String message) {
        sendMessage(message);
    }
}
