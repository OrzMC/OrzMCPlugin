package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.features.portal.PortalCommandService;
import com.jokerhub.paper.plugin.orzmc.infra.portal.IPortalService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class OrzPortalCommand implements CommandExecutor {
    private final PortalCommandService service;

    public OrzPortalCommand(IPortalService portalService, OrzTextStyles styles) {
        this.service = new PortalCommandService(portalService, styles);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(service.requirePlayerTip());
            return true;
        }
        PortalCommandService.Result result = service.handle(p, args);
        if (result instanceof PortalCommandService.Result.Success success) {
            p.sendMessage(success.message());
        } else if (result instanceof PortalCommandService.Result.Failure failure) {
            p.sendMessage(failure.message());
        }
        return true;
    }
}
