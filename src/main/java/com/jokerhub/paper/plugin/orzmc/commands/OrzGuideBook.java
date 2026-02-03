package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class OrzGuideBook implements CommandExecutor {
    private final GuideService guideService;

    public OrzGuideBook(ConfigService configService, OrzTextStyles styles) {
        this.guideService = new GuideService(configService, styles);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender commandSender,
            @NotNull Command command,
            @NotNull String s,
            String @NotNull [] strings) {
        if (commandSender instanceof Player player) {
            guideService.openGuide(player);
        }
        return false;
    }
}
