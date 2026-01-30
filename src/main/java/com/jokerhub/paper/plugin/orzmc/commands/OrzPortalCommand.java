package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.features.portal.PortalService;
import com.jokerhub.paper.plugin.orzmc.infra.core.ServiceRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class OrzPortalCommand implements CommandExecutor {
    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("需要玩家执行"));
            return true;
        }
        if (!p.isOp()) {
            p.sendMessage(OrzTextStyles.warn("仅 OP 可用"));
            return true;
        }
        if (args.length < 1) {
            p.sendMessage(OrzTextStyles.info("用法: /portal <host> [port] 或 /portal remove <host> [port]"));
            return true;
        }
        if ("remove".equalsIgnoreCase(args[0]) || "rm".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                p.sendMessage(OrzTextStyles.info("用法: /portal remove <host> [port]"));
                return true;
            }
            String host = args[1];
            int port = 25565;
            if (args.length >= 3) {
                try {
                    port = Integer.parseInt(args[2]);
                } catch (Exception e) {
                    p.sendMessage(OrzTextStyles.warn("端口需为数字"));
                    return true;
                }
            }
            String target = host + ":" + port;
            int removed = ServiceRegistry.portal().removeByTarget(target);
            if (removed <= 0) {
                p.sendMessage(OrzTextStyles.warn("没有匹配的传送门: " + target));
            } else {
                p.sendMessage(OrzTextStyles.success("已移除 " + removed + " 个传送门 -> " + target));
            }
        } else {
            String host = args[0];
            int port = 25565;
            if (args.length >= 2) {
                try {
                    port = Integer.parseInt(args[1]);
                } catch (Exception e) {
                    p.sendMessage(OrzTextStyles.warn("端口需为数字"));
                    return true;
                }
            }
            PortalService.PortalInfo info = ServiceRegistry.portal().createPortal(p, host, port);
            String msg = String.format(
                    "已创建传送门 -> %s:%d @ [%s] %d %d %d 轴向:%s 框架:4x5",
                    host,
                    port,
                    info.location().getWorld().getName(),
                    info.location().getBlockX(),
                    info.location().getBlockY(),
                    info.location().getBlockZ(),
                    info.axis().name());
            p.sendMessage(OrzTextStyles.success(msg));
        }
        return true;
    }
}
