package com.jokerhub.paper.plugin.orzmc.features.player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceModeService;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceModeService.MaintenanceProgress;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceModeService.MaintenanceReason;
import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRule;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.Map;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * 登录访问控制统一入口。
 *
 * <p>把维护模式、IP 黑名单、GeoIP 的 prelogin 编排收敛到同一处，事件监听器只负责转发。
 * 顺序固定为：维护模式 → 本地 IP 黑名单 → 玩家名规则 → GeoIP 国家/地区白名单。</p>
 */
public final class LoginAccessControlService {

    /** prelogin 拦截通知限频周期（毫秒）：被拦截的客户端会自动重连，每次 prelogin 都触发
     * 拦截——高频重试不节流会重复私信管理员（对照 whitelist_block 曾 48 次打爆 QQ 频控
     * 40034100 的事故）。控制台 warning 不受限频，保持每次可查。 */
    private static final long ACCESS_RULE_BLOCK_THROTTLE_MS = 5000L;

    private final MaintenanceModeService maintenanceModeService;
    private final AccessRuleService accessRuleService;
    private final GeoIpAccessService geoIpAccessService;
    private final PlayerEventService playerEventService;
    private final Notifier notifier;
    private final TypedConfigProvider configs;
    private final OrzTextStyles styles;
    private final ServerFacade server;
    private final ThrottledNotifier blockNotifier;

    public LoginAccessControlService(
            MaintenanceModeService maintenanceModeService,
            AccessRuleService accessRuleService,
            GeoIpAccessService geoIpAccessService,
            PlayerEventService playerEventService,
            Notifier notifier,
            TypedConfigProvider configs,
            OrzTextStyles styles,
            ServerFacade server,
            ThrottledNotifier blockNotifier) {
        this.maintenanceModeService = maintenanceModeService;
        this.accessRuleService = accessRuleService;
        this.geoIpAccessService = geoIpAccessService;
        this.playerEventService = playerEventService;
        this.notifier = notifier;
        this.configs = configs;
        this.styles = styles;
        this.server = server;
        this.blockNotifier = blockNotifier;
    }

    public void handlePreLogin(AsyncPlayerPreLoginEvent event) {
        if (maintenanceModeService.isActive()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, styles.warn(buildRejectText()));
            return;
        }
        if (!event.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.ALLOWED)) {
            return;
        }
        // getAddress() 在 prelogin 中通常恒有值，但做防御：为 null 视为无地址，
        // 跳过 IP/GeoIP 检查，玩家名规则仍照常生效。
        java.net.InetAddress address = event.getAddress();
        String ipAddress = address == null ? "" : address.getHostAddress();
        String playerName = playerName(event); // 可能为 null / 空串（离线模式 profile 未上报名称）
        String displayName = (playerName == null || playerName.isEmpty()) ? "未知玩家" : playerName;
        String matchedPattern = accessRuleService.matchedIpPattern(ipAddress);
        if (matchedPattern != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, styles.error("你的IP已被禁止访问"));
            notifyBanHit(displayName, ipAddress, matchedPattern);
            return;
        }
        // 名称未上报（null/空串）时跳过玩家名规则匹配：否则「未知玩家」会命中过宽规则（如 contains:"a"）
        // 而误封合法玩家；通知仍用 displayName 展示占位。
        PlayerNameRule matchedNameRule = (playerName == null || playerName.isEmpty())
                ? null
                : accessRuleService.matchedPlayerNameRule(playerName);
        if (matchedNameRule != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, styles.error("你的玩家名不符合服务器访问规则"));
            notifyPlayerNameBlocked(displayName, matchedNameRule);
            return;
        }
        if (ipAddress.isEmpty()) {
            return;
        }
        // 阻塞等待本次查询结果：只在异步处理器线程上等待，不阻塞主线程；
        // 超时/异常由 handleGeoIpPreLogin 内部按 fail-open 放行并告警。
        playerEventService.handleGeoIpPreLogin(
                event,
                displayName,
                ipAddress,
                geoIpAccessService.decide(ipAddress),
                GeoIpAccessService.DECISION_TIMEOUT_MS);
    }

    /**
     * 按维护原因渲染登录被拒文案：统一走 {@link MaintenanceModeService#renderMotdText}。
     *
     * <p>场景文案与进度行由 {@code templates.yml} 的 {@code maintenance_motd_*} 键驱动
     * （2026-09-02 PR4 迁移自 config.yml maintenance 段）：有进度时进度行以
     * {@code maintenance_motd_progress_line} 模板渲染为第二行；场景模板自带
     * {@code {stage}/{percent}/{eta}} 占位符则直接替换、不追加独立进度行。原「未用 {eta} 时
     * 追加空格+预计剩余 N 秒」尾缀逻辑已废弃（progress_line 默认含 {eta}；若服主自定义
     * progress_line 无 {eta} 则无预计剩余，尊重模板意图）。</p>
     */
    private String buildRejectText() {
        MaintenanceReason reason = maintenanceModeService.reason();
        MaintenanceProgress progress = maintenanceModeService.progress();
        return MaintenanceModeService.renderMotdText(reason, configs.templates(), progress);
    }

    /** 封禁命中（安全加固 P2-4）：PRIVATE 私信管理员 + 服务端日志。私信限频防重连刷屏，日志每次保留。 */
    private void notifyBanHit(String player, String ip, String pattern) {
        String fallback = "⚠ IP 黑名单拦截\n玩家: " + player + "\nIP: " + ip + "\n命中规则: " + pattern;
        MessageEnvelope env = configs.renderTemplate(
                TemplateKeys.IP_BLACKLIST_BLOCK, Map.of("player", player, "ip", ip, "pattern", pattern), fallback);
        if (blockNotifier.shouldRun("ip_blacklist_block", ACCESS_RULE_BLOCK_THROTTLE_MS)) {
            notifier.event(TemplateKeys.IP_BLACKLIST_BLOCK, env);
        }
        server.logger().warning("黑名单拦截: " + player + " (" + ip + ") 命中规则 " + pattern);
    }

    private void notifyPlayerNameBlocked(String player, PlayerNameRule rule) {
        String fallback = "⚠ 玩家名规则拦截\n玩家: " + player + "\n命中规则: " + rule.display();
        MessageEnvelope env = configs.renderTemplate(
                TemplateKeys.PLAYER_NAME_BLOCK, Map.of("player", player, "rule", rule.display()), fallback);
        if (blockNotifier.shouldRun("player_name_block", ACCESS_RULE_BLOCK_THROTTLE_MS)) {
            notifier.event(TemplateKeys.PLAYER_NAME_BLOCK, env);
        }
        server.logger().warning("玩家名规则拦截: " + player + " 命中规则 " + rule.display());
    }

    /** 返回 profile 上报的玩家名；未上报（离线模式防御）时返回 null，由调用方决定是否参与名称规则匹配。 */
    private static String playerName(AsyncPlayerPreLoginEvent event) {
        PlayerProfile profile = event.getPlayerProfile();
        return profile == null ? null : profile.getName();
    }
}
