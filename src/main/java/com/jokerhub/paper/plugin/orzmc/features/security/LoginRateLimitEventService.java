package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.LoginRateLimitConfig;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.profile.PlayerProfile;

/**
 * 进服限流事件编排（安全加固 P2-2）。
 *
 * <p>把 {@link LoginRateLimitService} 的纯判定接入事件侧：</p>
 * <ul>
 *   <li>{@link AsyncPlayerPreLoginEvent}：频率超限或同 IP 并发超限 → {@code disallow} +
 *       warn 提示 +（按 {@code notify_admins} 配置）PRIVATE 私信管理员（模板
 *       {@code login_rate_limit_alert}，Java 兜底文案防模板缺失）；</li>
 *   <li>{@link PlayerJoinEvent}：登记并发；{@link PlayerQuitEvent}：注销并发。</li>
 * </ul>
 */
public final class LoginRateLimitEventService {

    private final LoginRateLimitService limiter;
    private final TypedConfigProvider configs;
    private final Notifier notifier;
    private final OrzTextStyles styles;

    public LoginRateLimitEventService(
            LoginRateLimitService limiter, TypedConfigProvider configs, Notifier notifier, OrzTextStyles styles) {
        this.limiter = limiter;
        this.configs = configs;
        this.notifier = notifier;
        this.styles = styles;
    }

    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        LoginRateLimitConfig cfg = configs.loginRateLimit();
        if (!cfg.enabled()) {
            return;
        }
        InetAddress address = event.getAddress();
        if (address == null) {
            return;
        }
        String ip = address.getHostAddress();
        String reason = null;
        if (limiter.isRateLimited(ip)) {
            reason = "频率超限（" + cfg.maxLoginAttemptsPerMinute() + " 次/分钟）";
        } else if (limiter.isConcurrencyReached(ip)) {
            reason = "同 IP 并发超限（" + cfg.maxConcurrentPerIp() + " 人）";
        }
        if (reason == null) {
            return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, styles.warn(cfg.message()));
        if (cfg.notifyAdmins()) {
            notifyAdmin(ip, playerName(event), reason);
        }
    }

    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = ipOf(player.getAddress());
        if (ip == null) {
            return;
        }
        limiter.onPlayerJoin(ip, player.getName());
    }

    public void onPlayerQuit(PlayerQuitEvent event) {
        limiter.onPlayerQuit(event.getPlayer().getName());
    }

    private void notifyAdmin(String ip, String player, String reason) {
        String fallback = "⚠ 登录限流\nIP: " + ip + "\n玩家: " + player + "\n原因: " + reason;
        MessageEnvelope env = configs.renderTemplate(
                TemplateKeys.LOGIN_RATE_LIMIT_ALERT, Map.of("ip", ip, "player", player, "reason", reason), fallback);
        notifier.event(TemplateKeys.LOGIN_RATE_LIMIT_ALERT, env);
    }

    private static String ipOf(InetSocketAddress socket) {
        if (socket == null || socket.getAddress() == null) {
            return null;
        }
        return socket.getAddress().getHostAddress();
    }

    private static String playerName(AsyncPlayerPreLoginEvent event) {
        PlayerProfile profile = event.getPlayerProfile();
        return profile != null && profile.getName() != null ? profile.getName() : "未知玩家";
    }
}
