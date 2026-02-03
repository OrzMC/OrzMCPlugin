package com.jokerhub.paper.plugin.orzmc.features.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

public final class CommandFeedbackService {
    public TextComponent cooldownTip() {
        return Component.text("命令冷却中，请稍后再试");
    }

    public TextComponent adminRequiredTip() {
        return Component.text("需要管理员权限");
    }

    public TextComponent playerRequiredTip() {
        return Component.text("需要玩家执行");
    }

    public TextComponent usageTip(String text) {
        return Component.text(text);
    }

    public TextComponent portNumberRequiredTip() {
        return Component.text("端口需为数字");
    }
}
