package com.jokerhub.paper.plugin.orzmc.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HealthRegistry {
    public static final class Status {
        public boolean enabled;
        public boolean httpOk;
        public boolean wsConnected;
        public boolean apiReady;
        public String lastError;
        public long lastUpdated;
    }

    private static final Map<String, Status> map = new ConcurrentHashMap<>();

    public static Status get(String service) {
        return map.computeIfAbsent(service, k -> new Status());
    }

    public static void setEnabled(String service, boolean v) {
        Status s = get(service);
        s.enabled = v;
        s.lastUpdated = System.currentTimeMillis();
    }

    public static void setHttpOk(String service, boolean v) {
        Status s = get(service);
        s.httpOk = v;
        s.lastUpdated = System.currentTimeMillis();
    }

    public static void setWsConnected(String service, boolean v) {
        Status s = get(service);
        s.wsConnected = v;
        s.lastUpdated = System.currentTimeMillis();
    }

    public static void setApiReady(String service, boolean v) {
        Status s = get(service);
        s.apiReady = v;
        s.lastUpdated = System.currentTimeMillis();
    }

    public static void setLastError(String service, String msg) {
        Status s = get(service);
        s.lastError = msg;
        s.lastUpdated = System.currentTimeMillis();
    }
}
