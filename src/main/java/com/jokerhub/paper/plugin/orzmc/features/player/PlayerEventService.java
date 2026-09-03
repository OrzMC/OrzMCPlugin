package com.jokerhub.paper.plugin.orzmc.features.player;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.ExceptionFormatter;
import java.time.Duration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class PlayerEventService {
    /** GeoIP 异常告警私信的限频窗口：上游故障时避免每次登录都向管理员发一条 DM。 */
    private static final long GEOIP_ALERT_THROTTLE_MS = Duration.ofMinutes(1).toMillis();

    private static final String GEOIP_ALERT_THROTTLE_KEY = "geoip_exception_alert";

    /** geoip_block/geoip_unverifiable 群消息限频：GeoIP 故障期 fail-close 拒绝随客户端自动重连高频触发，
     * 不限频会重复打爆玩家群（对照 whitelist_block 曾 48 次打爆 QQ 频控 40034100 的事故）。
     * event.disallow 不受限频，拦截始终执行。 */
    private static final long GEOIP_BLOCK_THROTTLE_MS = 5000L;

    private static final String GEOIP_BLOCK_THROTTLE_KEY = "geoip_block_group_notify";

    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final OrzTextStyles styles;
    private final Notifier notifier;
    private final ThrottledNotifier throttledNotifier;
    private final PlayerEventAggregator aggregator;

    public PlayerEventService(
            ServerFacade server,
            TypedConfigProvider configs,
            OrzTextStyles styles,
            Notifier notifier,
            ThrottledNotifier throttledNotifier,
            PlayerEventAggregator aggregator) {
        this.server = server;
        this.configs = configs;
        this.styles = styles;
        this.notifier = notifier;
        this.throttledNotifier = throttledNotifier;
        this.aggregator = aggregator;
    }

    public enum PlayerState {
        JOIN,
        QUIT,
        KICK
    }

    public void handleGeoIpDecision(
            AsyncPlayerPreLoginEvent event, String playerName, String ipAddress, GeoIpAccessService.Decision decision) {
        // 上游查询失败/无法定位国家码 → 无论放行还是拒绝都告警管理员（区分放行/拒绝措辞）
        if (decision.lookupFailed()) {
            handleGeoIpLookupFailure(playerName, ipAddress, decision.allowed() ? "已放行（fail-open）" : "已拒绝（fail-close）");
        }
        if (decision.allowed()) {
            return;
        }
        if (decision.lookupFailed()) {
            // fail-close 拒绝：无法验证地区（解析服务临时故障），区别于「地区不在白名单」的确定性拦截
            denyGeoIpUnverifiable(event, playerName, ipAddress);
            return;
        }
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("name", playerName);
        vars.put("ip", ipAddress);
        vars.put("country_code", decision.countryCode());
        vars.put("allow_list", String.join(",", decision.allowList()));
        vars.put("address_info", formatAddressInfo(decision.rawJson()));
        MessageEnvelope envelope = configs.renderEvent("geoip_block", vars);
        notifyGeoIpBlock("geoip_block", envelope);
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                styles.error(playerName + "(" + ipAddress + ")" + "\n" + decision.countryCode() + "\n" + "IP位置不在服务支持区域"
                        + String.join(",", decision.allowList())));
    }

    /** 将 GeoIP 返回的原始 JSON 格式化为可读的多行形式；空或非法内容原样返回。 */
    static String formatAddressInfo(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return "";
        }
        try {
            return new com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(com.google.gson.JsonParser.parseString(rawJson));
        } catch (com.google.gson.JsonSyntaxException e) {
            return rawJson;
        }
    }

    public void handleGeoIpException(Throwable e) {
        sendGeoIpAlert("IP地址解析服务异常: " + e.toString(), ExceptionFormatter.summarize(e));
    }

    /**
     * 阻塞等待 GeoIP 决策结果并据此放行/拦截。
     *
     * <p>在异步的 AsyncPlayerPreLoginEvent 处理器内调用：只阻塞当前 netty 线程，
     * 不会阻塞主线程。超时/中断/异常按 {@code geoip.fail_open} 策略处理（默认 fail-close
     * 拒绝进入，可配置 fail-open 放行），两种情况都告警到日志与群。</p>
     *
     * @param decisionFuture GeoIP 查询的异步结果
     * @param timeoutMs 阻塞等待上限，超过则按超时处理
     */
    public void handleGeoIpPreLogin(
            AsyncPlayerPreLoginEvent event,
            String playerName,
            String ipAddress,
            java.util.concurrent.CompletableFuture<GeoIpAccessService.Decision> decisionFuture,
            long timeoutMs) {
        GeoIpAccessService.Decision decision;
        try {
            decision = decisionFuture.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            handleGeoIpTimeout(playerName, ipAddress, timeoutMs);
            if (!failOpen()) {
                denyGeoIpUnverifiable(event, playerName, ipAddress);
            }
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleGeoIpException(e);
            if (!failOpen()) {
                denyGeoIpUnverifiable(event, playerName, ipAddress);
            }
            return;
        } catch (Exception e) {
            handleGeoIpException(e);
            if (!failOpen()) {
                denyGeoIpUnverifiable(event, playerName, ipAddress);
            }
            return;
        }
        handleGeoIpDecision(event, playerName, ipAddress, decision);
    }

    public void handleGeoIpTimeout(String playerName, String ipAddress, long timeoutMs) {
        sendGeoIpAlert(
                "IP地址解析超时(" + timeoutMs + "ms)，" + (failOpen() ? "已放行" : "已拒绝") + ": " + playerName + "(" + ipAddress
                        + ")",
                "geoip lookup timeout");
    }

    /**
     * GeoIP 上游查询失败（非超时）时调用：告警到日志与管理员私信。
     *
     * <p>{@code outcome} 为放行/拒绝说明（如 {@code 已放行（fail-open）}）；经
     * {@link com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier} 路由为
     * {@code exception_alert}（PRIVATE → 各平台 admin_dm），不会发到玩家群。</p>
     */
    public void handleGeoIpLookupFailure(String playerName, String ipAddress, String outcome) {
        sendGeoIpAlert("IP地址解析服务异常，" + outcome + ": " + playerName + "(" + ipAddress + ")", "geoip lookup failed");
    }

    /** fail-close 拦截：GeoIP 无法验证地区（超时/中断/查询失败）时拒绝进入并渲染 geoip_unverifiable，提示玩家重试。 */
    private void denyGeoIpUnverifiable(AsyncPlayerPreLoginEvent event, String playerName, String ipAddress) {
        MessageEnvelope envelope =
                configs.renderEvent("geoip_unverifiable", java.util.Map.of("name", playerName, "ip", ipAddress));
        notifyGeoIpBlock("geoip_unverifiable", envelope);
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                styles.error(playerName + "(" + ipAddress + ")\n地区解析服务暂时不可用，无法验证你的地区，请稍后重新尝试登录"));
    }

    /** geoip_block/geoip_unverifiable 群消息限频发送（含玩家 IP 与白名单，不适合高频刷群）。 */
    private void notifyGeoIpBlock(String eventKey, MessageEnvelope env) {
        if (throttledNotifier.shouldRun(GEOIP_BLOCK_THROTTLE_KEY, GEOIP_BLOCK_THROTTLE_MS)) {
            notifier.event(eventKey, env);
        }
    }

    /** {@code geoip.fail_open} 配置（默认 {@code false} = fail-close，安全优先）。 */
    private boolean failOpen() {
        return configs.ipWhitelist().failOpen();
    }

    /** 统一的 GeoIP 异常告警：写日志并路由 {@code exception_alert}（PRIVATE → 管理员私信）。 */
    private void sendGeoIpAlert(String message, String stackSummary) {
        server.logger().warning(message);
        // 限频只抑制私信：上游故障时避免每次登录都打扰管理员，日志始终保留完整现场
        if (!throttledNotifier.shouldRun(GEOIP_ALERT_THROTTLE_KEY, GEOIP_ALERT_THROTTLE_MS)) {
            return;
        }
        MessageEnvelope envelope = configs.renderEvent(
                "exception_alert", java.util.Map.of("message", message, "stack_summary", stackSummary));
        notifier.event("exception_alert", envelope);
    }

    /**
     * 收纳入队一条上下线广播事件。
     *
     * <p>不再直接发送：事件进入 {@link PlayerEventAggregator} 聚合窗口，窗口尾部统一冲刷
     * （单发走原模板，多发走 {@code player_digest} 摘要）。限流通过窗口合并实现，
     * 不丢消息——每条事件要么作为唯一事件单条渲染，要么进入摘要被精确计数。</p>
     */
    public void notifyPlayerState(Player player, PlayerState state) {
        aggregator.enqueue(player, state);
    }

    /**
     * 立即冲刷聚合器中挂起的批次（同步）。
     *
     * <p>插件禁用/重载时调用：Bukkit 会取消插件待执行任务，窗口尾部调度可能来不及运行，
     * 此方法保证最后一个窗口的上下线事件在卸载前交付，不静默丢弃。</p>
     */
    public void flushPending() {
        aggregator.flushPending();
    }
}
