package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.RemoteServerCommandEvent;

/**
 * orzdebug 测试通道（RCON 专用）。
 *
 * <p>职责边界（Paper 26 实测）：游戏内/控制台（stdin）的 {@code /orzdebug} 是 Brigadier
 * 注册命令，由 FeatureModule 的 executes() 直调 {@code BotInboundHandler} 处理，
 * <b>不</b>触发 ServerCommandEvent——因此本类<b>只监听</b> {@link RemoteServerCommandEvent}
 * （RCON 命令不走 Brigadier，仍触发事件）。两通道入口互斥，不会双重处理。</p>
 *
 * <p>前代实现同时监听 {@code ServerCommandEvent}，会与 executes 直调构成双通道（Paper
 * 若恢复事件分发将导致同命令处理两次 → 群消息重复），已移除。</p>
 */
public class OrzDebugEvent extends OrzBaseListener {
    private final BotInboundHandler inboundHandler;

    public OrzDebugEvent(OrzMC plugin, BotInboundHandler inboundHandler) {
        super(plugin);
        this.inboundHandler = inboundHandler;
    }

    /** RCON 命令监听（RCON 不走 Brigadier，仍触发 RemoteServerCommandEvent）。 */
    @EventHandler
    public void rconDebugHandler(RemoteServerCommandEvent event) {
        String rawCommand = event.getCommand();
        String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        String debugCmdPrefix = "orzdebug";
        if (!command.startsWith(debugCmdPrefix)) {
            return;
        }
        String cmd = command.substring(debugCmdPrefix.length()).trim();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                inboundHandler.handleMessage(
                        cmd, true, "RCON", env -> plugin.getLogger().info("cmd debug: \n" + env.message()));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "debug 命令异步执行异常", e);
            }
        });
    }
}
