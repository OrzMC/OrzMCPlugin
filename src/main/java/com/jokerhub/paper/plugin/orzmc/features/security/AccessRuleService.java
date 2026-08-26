package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 访问规则服务：统一管理 IP 黑名单与玩家名规则。
 *
 * <p>IP 规则沿用精确 IP / CIDR / 通配符三种匹配；玩家名规则支持精确、前缀、后缀、
 * 包含、glob 与正则。运行时规则持久化到 {@code access_rules.yml}。</p>
 */
public final class AccessRuleService {

    private static final String CONFIG_NAME = "access_rules";
    private static final String IP_PATH = "ip_blacklist";
    private static final String PLAYER_NAME_PATH = "player_name_rules";

    private final ConfigService configService;
    private final java.util.logging.Logger logger;
    private volatile List<String> ipPatterns = List.of();
    private volatile List<PlayerNameRule> playerNameRules = List.of();

    public AccessRuleService(ConfigService configService) {
        this(configService, java.util.logging.Logger.getLogger("OrzMC"));
    }

    public AccessRuleService(ConfigService configService, java.util.logging.Logger logger) {
        this.configService = configService;
        this.logger = logger;
        reload();
    }

    // ---- IP rules ----

    public boolean isIpBlocked(String ip) {
        return matchedIpPattern(ip) != null;
    }

    /**
     * 返回第一个命中的 IP 规则；未命中返回 {@code null}。
     */
    public String matchedIpPattern(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        for (String pattern : ipPatterns) {
            if (matches(ip, pattern)) return pattern;
        }
        return null;
    }

    /**
     * 添加 IP 规则；返回 {@code true} 表示确有新规则加入，{@code false} 表示参数非法或已存在（去重）。
     *
     * <p>入口 trim：游戏侧 Brigadier {@code greedyString} 保留尾随空格，bot 侧会 trim，
     * 统一在服务层归一化可避免「带空格规则加不进、删不掉」的分叉。去重按
     * {@link #samePattern} 判定——IPv6 规范等价变体（大小写/前导零/压缩）视为同一条。</p>
     */
    public synchronized boolean addIpPattern(String pattern) {
        if (pattern == null) return false;
        String trimmed = pattern.trim();
        if (trimmed.isEmpty()) return false;
        for (String existing : ipPatterns) {
            if (samePattern(existing, trimmed)) return false;
        }
        List<String> updated = new ArrayList<>(ipPatterns);
        updated.add(trimmed);
        this.ipPatterns = Collections.unmodifiableList(updated);
        persist();
        return true;
    }

    /**
     * 移除 IP 规则；返回 {@code true} 表示确有规则被移除（供命令侧区分「已移除」与「未找到」）。
     * 同样按 {@link #samePattern} 命中，IPv6 规范等价变体可正常移除。
     */
    public synchronized boolean removeIpPattern(String pattern) {
        if (pattern == null) return false;
        String trimmed = pattern.trim();
        if (trimmed.isEmpty()) return false;
        List<String> updated = new ArrayList<>(ipPatterns);
        if (updated.removeIf(existing -> samePattern(existing, trimmed))) {
            this.ipPatterns = Collections.unmodifiableList(updated);
            persist();
            return true;
        }
        return false;
    }

    public List<String> getIpPatterns() {
        return ipPatterns;
    }

    // ---- player name rules ----

    public boolean isPlayerNameBlocked(String name) {
        return matchedPlayerNameRule(name) != null;
    }

    /** 返回第一个命中的玩家名规则；未命中返回 {@code null}。 */
    public PlayerNameRule matchedPlayerNameRule(String name) {
        if (name == null || name.isEmpty()) return null;
        for (PlayerNameRule rule : playerNameRules) {
            if (rule.matches(name)) return rule;
        }
        return null;
    }

    /**
     * 添加玩家名规则；返回 {@code true} 表示确有新规则加入，{@code false} 表示参数非法或已存在（去重）。
     *
     * <p>入口 trim 规则值：与 IP 规则同理，两端命令输入归一化后持久化，避免带尾随空格的
     * 规则永不命中且无法移除。</p>
     */
    public synchronized boolean addPlayerNameRule(PlayerNameRule.MatchType type, String value) {
        if (type == null || value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;
        PlayerNameRule rule = PlayerNameRule.of(type, trimmed);
        if (!rule.isValid()) return false;
        if (containsRule(playerNameRules, rule)) return false;
        List<PlayerNameRule> updated = new ArrayList<>(playerNameRules);
        updated.add(rule);
        this.playerNameRules = Collections.unmodifiableList(updated);
        persist();
        return true;
    }

    /** 移除玩家名规则；返回 {@code true} 表示确有规则被移除（供命令侧区分「已移除」与「未找到」）。 */
    public synchronized boolean removePlayerNameRule(PlayerNameRule.MatchType type, String value) {
        if (type == null || value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;
        PlayerNameRule target = PlayerNameRule.of(type, trimmed);
        List<PlayerNameRule> updated = new ArrayList<>(playerNameRules);
        if (updated.removeIf(existing -> sameRule(existing, target))) {
            this.playerNameRules = Collections.unmodifiableList(updated);
            persist();
            return true;
        }
        return false;
    }

    public List<PlayerNameRule> getPlayerNameRules() {
        return playerNameRules;
    }

    private static boolean containsRule(List<PlayerNameRule> rules, PlayerNameRule target) {
        for (PlayerNameRule existing : rules) {
            if (sameRule(existing, target)) return true;
        }
        return false;
    }

    private static boolean sameRule(PlayerNameRule left, PlayerNameRule right) {
        return left.type() == right.type() && left.value().equalsIgnoreCase(right.value());
    }

    // ---- persistence ----

    /**
     * 从磁盘重载规则。synchronized 与 add/remove 互斥，避免「重载读旧快照」在变更间隙
     * 覆盖刚提交的内存规则（并发丢失更新）。
     */
    public synchronized void reload() {
        this.ipPatterns = loadIpPatterns();
        this.playerNameRules = loadPlayerNameRules();
    }

    private List<String> loadIpPatterns() {
        FileConfiguration cfg = configService.getConfig(CONFIG_NAME);
        if (cfg == null) return List.of();
        return Collections.unmodifiableList(cfg.getStringList(IP_PATH));
    }

    private List<PlayerNameRule> loadPlayerNameRules() {
        FileConfiguration cfg = configService.getConfig(CONFIG_NAME);
        if (cfg == null) return List.of();
        List<?> raw = cfg.getList(PLAYER_NAME_PATH);
        if (raw == null) return List.of();
        List<PlayerNameRule> rules = new ArrayList<>();
        for (Object item : raw) {
            String type = null;
            String value = null;
            if (item instanceof Map<?, ?> map) {
                type = stringValue(map.get("type"));
                value = stringValue(map.get("value"));
            } else if (item instanceof ConfigurationSection section) {
                type = section.getString("type");
                value = section.getString("value");
            } else if (item instanceof String text) {
                int colon = text.indexOf(':');
                if (colon > 0) {
                    type = text.substring(0, colon);
                    value = text.substring(colon + 1);
                }
            }
            PlayerNameRule.MatchType matchType = PlayerNameRule.MatchType.from(type);
            if (matchType != null && value != null && !value.isBlank()) {
                PlayerNameRule rule = PlayerNameRule.of(matchType, value);
                if (rule.isValid()) rules.add(rule);
            }
        }
        return Collections.unmodifiableList(rules);
    }

    /**
     * 原子落盘：经 {@link ConfigService#updateConfig} 在同步块内完成 set→save。
     *
     * <p>若先 {@code getConfig} 拿实例、在 get/set 间隙被 {@code reloadConfig} 替换实例，
     * set 会写进已废弃对象而丢失——因此 set+save 必须整体放入 updateConfig 的同步块。
     * 本方法自身也 synchronized，保证对同一 {@code access_rules} 的多次变更串行化。</p>
     */
    private synchronized void persist() {
        boolean saved = configService.updateConfig(CONFIG_NAME, cfg -> {
            cfg.set(IP_PATH, new ArrayList<>(ipPatterns));
            List<Map<String, String>> serialized = new ArrayList<>();
            for (PlayerNameRule rule : playerNameRules) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("type", rule.type().display());
                entry.put("value", rule.value());
                serialized.add(entry);
            }
            cfg.set(PLAYER_NAME_PATH, serialized);
        });
        if (!saved) {
            // 内存规则已生效但未落盘：下次 reload 会静默消失，须显式告警
            logger.warning("访问规则落盘失败：access_rules 配置未注册或写入失败，规则仅存于内存");
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // ---- IP matching ----

    private static boolean matches(String ip, String pattern) {
        if (pattern.contains("/")) return cidrMatches(ip, pattern);
        if (pattern.contains("*")) return wildcardMatches(ip, pattern);
        return exactMatches(ip, pattern);
    }

    /**
     * 规则等价判定（去重/移除用）：字符串相等，或两者都是合法 IP 且规范字节相等。
     *
     * <p>仅当任一侧含 {@code ':'}（IPv6）才尝试 InetAddress 规范化——IPv6 有大小写/
     * 前导零/压缩等书写变体，IPv4 无此问题且可避免对 {@code 10.*} 等非 IP 模式触发
     * 解析。语义与 {@link #exactMatches} 一致。</p>
     */
    private static boolean samePattern(String a, String b) {
        if (a == null || b == null) return a == b;
        if (a.equals(b)) return true;
        if (a.indexOf(':') < 0 && b.indexOf(':') < 0) return false;
        try {
            return Arrays.equals(
                    InetAddress.getByName(a).getAddress(),
                    InetAddress.getByName(b).getAddress());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean exactMatches(String ip, String pattern) {
        if (ip.equals(pattern)) return true;
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
            if (ipBytes.length != subnetBytes.length) return false;
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
        // IPv4 专用通配：* 匹配 1 个或多个剩余网段。IPv6 请使用 CIDR。
        // 严格校验：IP 必须是恰好 4 段、每段 0-255 的合法 IPv4，
        // 拒绝非法 octet（如 10.999.999）与超 4 段的畸形地址（如 10.1.2.3.4）。
        // IPv4-mapped IPv6（::ffff:a.b.c.d，双栈服务器 prelogin 常见形态）先还原为 IPv4 再按通配匹配，
        // 与 exact/CIDR（InetAddress 规范化后命中）口径一致，避免通配黑名单被 mapped 形式静默绕过。
        String candidate = ipv4MappedToIpv4(ip);
        if (candidate == null) return false;
        String[] ipOctets = candidate.split("\\.", -1);
        if (ipOctets.length != 4) return false;
        for (String octet : ipOctets) {
            if (!isValidIpv4Octet(octet)) return false;
        }
        String[] segments = pattern.split("\\.", -1);
        for (String segment : segments) {
            if (segment.isEmpty()) return false; // 拒绝 "10." / "10..*" 等畸形模式
        }
        return matchWildcardSegments(segments, 0, ipOctets, 0);
    }

    /** 把 IPv4-mapped IPv6（::ffff:a.b.c.d，与 {@code isPrivateIp} 的识别口径一致）还原为 IPv4 串；
     * 非 mapped 形式原样返回；null 表示无法得到可用的 IPv4 字面量。 */
    private static String ipv4MappedToIpv4(String ip) {
        if (ip == null) return null;
        String lower = ip.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("::ffff:")) return ip;
        String v4 = ip.substring("::ffff:".length());
        return v4.isEmpty() ? null : v4;
    }

    /** 逐段匹配：{@code *} 匹配 1 个或多个剩余网段，字面段与 IP 段严格相等。 */
    private static boolean matchWildcardSegments(String[] segments, int si, String[] ipOctets, int oi) {
        if (si == segments.length) return oi == ipOctets.length;
        if (oi >= ipOctets.length) return false;
        String segment = segments[si];
        if ("*".equals(segment)) {
            for (int take = 1; oi + take <= ipOctets.length; take++) {
                if (matchWildcardSegments(segments, si + 1, ipOctets, oi + take)) return true;
            }
            return false;
        }
        return segment.equals(ipOctets[oi]) && matchWildcardSegments(segments, si + 1, ipOctets, oi + 1);
    }

    /** 单段合法性：1-3 位十进制数，值域 0-255。 */
    private static boolean isValidIpv4Octet(String octet) {
        if (octet.isEmpty() || octet.length() > 3) return false;
        for (int i = 0; i < octet.length(); i++) {
            if (!Character.isDigit(octet.charAt(i))) return false;
        }
        try {
            return Integer.parseInt(octet) <= 255;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
