package com.jokerhub.paper.plugin.orzmc.features.menu;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.bukkit.entity.Player;

public final class MenuCommandService {
    private final MenuService service;

    public MenuCommandService(OrzTextStyles styles) {
        this.service = new MenuService(styles);
    }

    public sealed interface Result permits Result.Success, Result.Failure {
        record Success() implements Result {}

        record Failure() implements Result {}
    }

    public Result handle(Player p) {
        service.openMenu(p);
        return new Result.Success();
    }
}
