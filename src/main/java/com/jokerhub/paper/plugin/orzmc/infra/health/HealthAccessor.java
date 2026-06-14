package com.jokerhub.paper.plugin.orzmc.infra.health;

import com.jokerhub.paper.plugin.orzmc.core.ports.health.HealthStatus;

/**
 * 将 {@link HealthRegistry} 适配为 {@link HealthStatus} 接口。
 *
 * <p>Feature 层通过此实现读取健康状态，而不直接依赖实现细节。</p>
 */
public final class HealthAccessor implements HealthStatus {

    private final HealthRegistry healthRegistry;

    public HealthAccessor(HealthRegistry healthRegistry) {
        this.healthRegistry = healthRegistry;
    }

    @Override
    public Entry get(String service) {
        HealthRegistry.Status s = healthRegistry.getRaw(service);
        return new Entry(s.enabled, s.httpOk, s.wsConnected, s.apiReady, s.lastError, s.lastUpdated);
    }
}
