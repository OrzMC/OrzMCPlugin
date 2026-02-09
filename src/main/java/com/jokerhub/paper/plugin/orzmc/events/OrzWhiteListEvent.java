package com.jokerhub.paper.plugin.orzmc.events;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.event.server.WhitelistToggleEvent;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistEventService;
import org.bukkit.event.EventHandler;

public class OrzWhiteListEvent extends OrzBaseListener {
    private final WhitelistEventService service;

    public OrzWhiteListEvent(OrzMC plugin, WhitelistEventService service) {
        super(plugin);
        this.service = service;
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
