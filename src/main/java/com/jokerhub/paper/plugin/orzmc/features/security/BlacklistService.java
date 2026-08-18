package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
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
        return matchedPattern(ip) != null;
    }

    /**
     * 返回第一个命中的规则；未命中返回 {@code null}。
     *
     * <p>安全加固 P2-4：封禁命中告警需要展示命中的具体规则（如 IPv6 CIDR 段）。</p>
     */
    public String matchedPattern(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        for (String pattern : patterns) {
            if (matches(ip, pattern)) return pattern;
        }
        return null;
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
        if (ip.equals(pattern)) return true;
        // IPv6 有多种合法文本形式（如 2001:db8::1 与 2001:0db8:0:0:0:0:0:1），
        // 双方均可解析为 InetAddress 时按字节序列比较；纯 IPv4 字符串不同即不同。
        if (ip.indexOf(':') < 0 && pattern.indexOf(':') < 0) return false;
        try {
            return Arrays.equals(
                    InetAddress.getByName(ip).getAddress(),
                    InetAddress.getByName(pattern).getAddress());
        } catch (Exception e) {
            return false;
        }
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

            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            byte[] subnetBytes = InetAddress.getByName(subnetStr).getAddress();
            if (ipBytes.length != subnetBytes.length) return false; // IPv4 与 IPv6 不匹配
            if (prefix < 0 || prefix > ipBytes.length * 8) return false;

            return matchesPrefix(ipBytes, subnetBytes, prefix);
        } catch (Exception e) {
            return false;
        }
    }

    /** 逐字节比较前 {@code prefix} 位（IPv4 最多 32 位，IPv6 最多 128 位）。 */
    private static boolean matchesPrefix(byte[] ip, byte[] subnet, int prefix) {
        int fullBytes = prefix / 8;
        int remBits = prefix % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (ip[i] != subnet[i]) return false;
        }
        if (remBits > 0) {
            int mask = 0xFF << (8 - remBits);
            if ((ip[fullBytes] & mask) != (subnet[fullBytes] & mask)) return false;
        }
        return true;
    }

    private static boolean wildcardMatches(String ip, String pattern) {
        // IPv4 专用通配：* 匹配剩余网段。IPv6 请使用 CIDR（IPv6 地址含冒号，此正则不会命中）。
        // 10.*        → 10\.\d{1,3}(\.\d{1,3})*
        // 192.168.*   → 192\.168\.\d{1,3}(\.\d{1,3})*
        String regex = "^" + pattern.replace(".", "\\.").replace("*", "\\d{1,3}(\\.\\d{1,3})*") + "$";
        return ip.matches(regex);
    }
}
