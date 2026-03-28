package com.jokerhub.paper.plugin.orzmc.infra.config;

public final class SafeKeys {
    private SafeKeys() {}

    public static String encodeTargetKey(String target) {
        if (target == null) return "";
        return target.replace('.', '_');
    }

    public static String decodeTargetKey(String key) {
        if (key == null) return "";
        return key.replace('_', '.');
    }
}
