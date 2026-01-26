package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.utils.OrzMessageParser;
import com.jokerhub.paper.plugin.orzmc.utils.OrzTextStyles;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerCommandEvent;

public class OrzDebugEvent extends OrzBaseListener {
    public OrzDebugEvent(OrzMC plugin) {
        super(plugin);
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
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> OrzMessageParser.parse(cmd, true, result -> {
            plugin.getLogger().info("cmd debug: \n" + result);
            plugin.getServer().sendMessage(OrzTextStyles.info("cmd debug: \n" + result));
        }));
    }
}
