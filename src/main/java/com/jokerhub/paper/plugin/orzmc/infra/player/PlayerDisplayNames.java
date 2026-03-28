package com.jokerhub.paper.plugin.orzmc.infra.player;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public final class PlayerDisplayNames {
    private PlayerDisplayNames() {}

    public static String format(Player player) {
        String ret = player.getPlayerProfile().getName();
        if (player.isOp()) {
            ret += "(op)";
        }
        String gameMode = "";
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE) gameMode = "创造";
        else if (gm == GameMode.SURVIVAL) gameMode = "生存";
        else if (gm == GameMode.ADVENTURE) gameMode = "冒险";
        else if (gm == GameMode.SPECTATOR) gameMode = "观察";
        ret += " " + gameMode + "模式";
        return ret;
    }
}
