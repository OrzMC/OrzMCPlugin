package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

public final class BlacklistService {

    private static final String CONFIG_NAME = "ip_blacklist";
    private static final String CONFIG_PATH = "ip_blacklist";

    private final ConfigService configService;
    private volatile List<String> patterns = List.of();

    public BlacklistService(ConfigService configService) {
        this.configService = configService;
        reload();
    }

    // ---- query ----

    public boolean isBlocked(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        for (String pattern : patterns) {
            if (matches(ip, pattern)) return true;
        }
        return false;
    }

    // ---- mutate ----

    public synchronized void add(String pattern) {
        if (pattern == null || pattern.isEmpty()) return;
        // prevent duplicates
        for (String existing : patterns) {
            if (existing.equals(pattern)) return;
        }
        List<String> updated = new ArrayList<>(patterns);
        updated.add(pattern);
        this.patterns = Collections.unmodifiableList(updated);
        persist(updated);
    }

    public synchronized void remove(String pattern) {
        if (pattern == null || pattern.isEmpty()) return;
        List<String> updated = new ArrayList<>(patterns);
        if (updated.remove(pattern)) {
            this.patterns = Collections.unmodifiableList(updated);
            persist(updated);
        }
    }

    public List<String> getPatterns() {
        return patterns;
    }

    // ---- persistence ----

    public void reload() {
        this.patterns = loadPatterns();
    }

    private List<String> loadPatterns() {
        FileConfiguration cfg = configService.getConfig(CONFIG_NAME);
        if (cfg == null) return List.of();
        List<String> list = cfg.getStringList(CONFIG_NAME);
        return Collections.unmodifiableList(list);
    }

    private void persist(List<String> list) {
        FileConfiguration cfg = configService.getConfig(CONFIG_NAME);
        if (cfg == null) return;
        cfg.set(CONFIG_PATH, new ArrayList<>(list));
        configService.saveConfig(CONFIG_NAME);
    }

    // ---- IP matching ----

    private static boolean matches(String ip, String pattern) {
        if (pattern.contains("/")) return cidrMatches(ip, pattern);
        if (pattern.contains("*")) return wildcardMatches(ip, pattern);
        return exactMatches(ip, pattern);
    }

    private static boolean exactMatches(String ip, String pattern) {
        return ip.equals(pattern);
    }

    private static boolean cidrMatches(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) return false;
            String subnetStr = parts[0];
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (prefix < 0 || prefix > 32) return false;

            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            byte[] subnetBytes = InetAddress.getByName(subnetStr).getAddress();
            if (ipBytes.length != 4 || subnetBytes.length != 4) return false;

            int ipInt = toInt(ipBytes);
            int subnetInt = toInt(subnetBytes);
            int mask = prefix == 0 ? 0 : 0xFFFFFFFF << (32 - prefix);

            return (ipInt & mask) == (subnetInt & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean wildcardMatches(String ip, String pattern) {
        // Convert wildcard pattern to regex: * matches remaining octets
        // 10.*        → 10\.\d{1,3}(\.\d{1,3})*
        // 192.168.*   → 192\.168\.\d{1,3}(\.\d{1,3})*
        String regex = "^" + pattern.replace(".", "\\.").replace("*", "\\d{1,3}(\\.\\d{1,3})*") + "$";
        return ip.matches(regex);
    }

    private static int toInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }
}
