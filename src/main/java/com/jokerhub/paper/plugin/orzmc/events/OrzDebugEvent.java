package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotInboundHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerCommandEvent;

public class OrzDebugEvent extends OrzBaseListener {
    private final BotInboundHandler inboundHandler;

    public OrzDebugEvent(OrzMC plugin, BotInboundHandler inboundHandler) {
        super(plugin);
        this.inboundHandler = inboundHandler;
    }

    public static boolean debug = false;

    @EventHandler
    public void cmdDebugHandler(ServerCommandEvent event) {
        String debugCmdPrefix = "debug";
        debug = event.getCommand().startsWith(debugCmdPrefix);
        if (!debug) {
            return;
        }
        String cmd = event.getCommand().substring(debugCmdPrefix.length()).trim();
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> inboundHandler.handleMessage(
                                cmd, true, env -> plugin.getLogger().info("cmd debug: \n" + env.message())));
    }
}
