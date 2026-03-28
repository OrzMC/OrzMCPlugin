package com.jokerhub.paper.plugin.orzmc.features.portal;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalInfo;
import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalPort;
import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandPermissionService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;

public final class PortalCommandService {
    private final CommandFeedbackService feedbackService = new CommandFeedbackService();
    private final CommandPermissionService permissionService = new CommandPermissionService();
    private final PortalPort portalService;
    private final OrzTextStyles styles;

    public PortalCommandService(PortalPort portalService, OrzTextStyles styles) {
        this.portalService = portalService;
        this.styles = styles;
    }

    public sealed interface Result permits Result.Success, Result.Failure {
        record Success(TextComponent message) implements Result {}

        record Failure(TextComponent message) implements Result {}
    }

    public Result handle(Player player, String[] args) {
        CommandPermissionService.PermissionResult pr = permissionService.requireAdmin(player);
        if (!pr.allowed()) {
            return new Result.Failure(pr.message());
        }
        if (args == null || args.length < 1) {
            return new Result.Failure(styles.info(feedbackService
                    .usageTip("用法: /portal <host> [port] 或 /portal remove <host> [port]")
                    .content()));
        }
        if ("remove".equalsIgnoreCase(args[0]) || "rm".equalsIgnoreCase(args[0])) {
            return handleRemove(player, args);
        }
        return handleCreate(player, args);
    }

    private Result handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            return new Result.Failure(styles.info(
                    feedbackService.usageTip("用法: /portal remove <host> [port]").content()));
        }
        String host = args[1];
        int port = 25565;
        if (args.length >= 3) {
            try {
                port = Integer.parseInt(args[2]);
            } catch (Exception e) {
                return new Result.Failure(
                        styles.warn(feedbackService.portNumberRequiredTip().content()));
            }
        }
        String target = host + ":" + port;
        int removed = portalService.removeByTarget(target);
        if (removed <= 0) {
            return new Result.Success(styles.warn("没有匹配的传送门: " + target));
        }
        return new Result.Success(styles.success("已移除 " + removed + " 个传送门 -> " + target));
    }

    private Result handleCreate(Player player, String[] args) {
        String host = args[0];
        int port = 25565;
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (Exception e) {
                return new Result.Failure(
                        styles.warn(feedbackService.portNumberRequiredTip().content()));
            }
        }
        PortalInfo info = portalService.createPortal(player, host, port);
        String msg = String.format(
                "已创建传送门 -> %s:%d @ [%s] %d %d %d 轴向:%s 框架:4x5",
                host,
                port,
                info.location().getWorld().getName(),
                info.location().getBlockX(),
                info.location().getBlockY(),
                info.location().getBlockZ(),
                info.axis().name());
        return new Result.Success(styles.success(msg));
    }

    public Component requirePlayerTip() {
        return feedbackService.playerRequiredTip();
    }
}
