package com.jokerhub.paper.plugin.orzmc.infra.core;

import com.jokerhub.paper.plugin.orzmc.infra.portal.IPortalService;

public final class ServiceRegistry {
    private static IPortalService portalService;

    public static void registerPortal(IPortalService svc) {
        portalService = svc;
    }

    public static IPortalService portal() {
        return portalService;
    }
}
