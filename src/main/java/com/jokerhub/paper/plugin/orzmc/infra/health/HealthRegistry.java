package com.jokerhub.paper.plugin.orzmc.infra.health;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务健康状态注册表（实例版）。
 *
 * <p>每个插件实例拥有独立的 {@link HealthRegistry}，通过构造器注入。
 * 不再使用全局静态状态，测试可获得隔离实例。</p>
 */
public final class HealthRegistry {

    public static final class Status {
        public volatile boolean enabled;
        public volatile boolean httpOk;
        public volatile boolean httpChecked;
        public volatile boolean wsConnected;
        public volatile boolean apiReady;
        public volatile String lastError;
        public volatile int deliveryFailed;
        public volatile int deliveryTotal;
        public volatile List<String> deliveryTargets;
        public volatile long lastUpdated;
    }

    private final Map<String, Status> map = new ConcurrentHashMap<>();

    public Status get(String service) {
        return map.computeIfAbsent(service, k -> new Status());
    }

    public Status getRaw(String service) {
        return map.computeIfAbsent(service, k -> new Status());
    }

    public void setEnabled(String service, boolean v) {
        Status s = get(service);
        s.enabled = v;
        s.lastUpdated = System.currentTimeMillis();
    }

    public void setHttpOk(String service, boolean v) {
        Status s = get(service);
        s.httpOk = v;
        s.httpChecked = true;
        s.lastUpdated = System.currentTimeMillis();
    }

    public void setHttpChecked(String service, boolean v) {
        Status s = get(service);
        s.httpChecked = v;
        if (!v) {
            s.httpOk = false;
            s.apiReady = false;
        }
        s.lastUpdated = System.currentTimeMillis();
    }

    public void setWsConnected(String service, boolean v) {
        Status s = get(service);
        s.wsConnected = v;
        s.lastUpdated = System.currentTimeMillis();
    }

    public void setApiReady(String service, boolean v) {
        Status s = get(service);
        s.apiReady = v;
        s.lastUpdated = System.currentTimeMillis();
    }

    public void setLastError(String service, String msg) {
        Status s = get(service);
        s.lastError = msg;
        s.lastUpdated = System.currentTimeMillis();
    }

    public void setDelivery(String service, int failed, int total, List<String> targets) {
        Status s = get(service);
        s.deliveryFailed = failed;
        s.deliveryTotal = total;
        s.deliveryTargets = targets == null ? List.of() : List.copyOf(targets);
        s.lastUpdated = System.currentTimeMillis();
    }
}
