package com.jokerhub.paper.plugin.orzmc.features.teleport;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;

public final class TeleportBowTexts {
    private final OrzTextStyles styles;

    public TeleportBowTexts(OrzTextStyles styles) {
        this.styles = styles;
    }

    public Component logText(String content) {
        if (!content.isEmpty()) {
            return Component.text()
                    .append(styles.tpbowPrefix())
                    .append(Component.space())
                    .append(Component.text(content))
                    .build();
        }
        return Component.empty();
    }
}
