package com.jokerhub.paper.plugin.orzmc.events;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.event.server.WhitelistToggleEvent;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistEventService;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.bukkit.event.EventHandler;

public class OrzWhiteListEvent extends OrzBaseListener {
    private final WhitelistEventService service;

    public OrzWhiteListEvent(OrzMC plugin, ConfigService configService, OrzTextStyles styles, Notifier notifier) {
        super(plugin);
        this.service = new WhitelistEventService(configService, styles, notifier);
    }

    @EventHandler
    public void onWhitelistVerify(ProfileWhitelistVerifyEvent event) {
        service.handleVerify(event);
    }

    @EventHandler
    public void onWhitelistToggled(WhitelistToggleEvent event) {
        service.handleToggle(event);
    }
}
