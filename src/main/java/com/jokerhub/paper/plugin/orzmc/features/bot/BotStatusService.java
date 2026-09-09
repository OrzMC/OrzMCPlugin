package com.jokerhub.paper.plugin.orzmc.features.bot;

import com.jokerhub.paper.plugin.orzmc.core.ports.health.HealthStatus;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

public final class BotStatusService {

    /** http / websocket 异常时点击跳转的详情命令。 */
    private static final String HTTP_DETAIL = "/bot http";

    private static final String WS_DETAIL = "/bot ws";

    private final OrzTextStyles styles;
    private final HealthStatus health;
    private final I18nService i18n;

    public BotStatusService(OrzTextStyles styles, HealthStatus health, I18nService i18n) {
        this.styles = styles;
        this.health = health;
        this.i18n = i18n;
    }

    /** /bot 为运维命令：状态文案按 default_lang（R1）决议；i18n 未注入时回落词表 key（测试 zh 注入全覆盖）。 */
    private String t(String key) {
        return i18n == null ? key : i18n.msg(i18n.langFor(), key);
    }

    /**
     * 最简状态反馈：enabled / http / websocket 三个彩色状态词，用颜色表达是否正常。
     * 仅 http 与 websocket 在异常时可点击查看详情；enabled 始终不可点击。
     */
    public Component buildMinimalMessage() {
        HealthStatus.Entry e = health.get("easybot");
        Component enabled = e.enabled()
                ? styles.success(t(MessageKeys.BOTSTATUS_STATE_ENABLED))
                : styles.error(t(MessageKeys.BOTSTATUS_STATE_DISABLED));
        Component http = httpWord(e);
        if (httpAbnormal(e)) {
            http = attachDetail(http, "http", HTTP_DETAIL);
        }
        Component ws = e.wsConnected()
                ? styles.success(t(MessageKeys.BOTSTATUS_STATE_WS_OK))
                : styles.error(t(MessageKeys.BOTSTATUS_STATE_WS_NOT_OK));
        if (!e.wsConnected()) {
            ws = attachDetail(ws, "websocket", WS_DETAIL);
        }
        return Component.empty()
                .append(enabled)
                .append(Component.space())
                .append(http)
                .append(Component.space())
                .append(ws);
    }

    /** HTTP 详情：连接状态（含投递结果）+ 失败平台逐行列出 + 最近错误。 */
    public Component buildHttpDetail() {
        HealthStatus.Entry e = health.get("easybot");
        List<Component> lines = new ArrayList<>();
        lines.add(detailLabel("HTTP", httpState(e)));
        if (e.deliveryFailed() > 0) {
            boolean allFailed = e.deliveryTotal() > 0 && e.deliveryFailed() >= e.deliveryTotal();
            String header = e.deliveryTotal() > 0
                    ? t(MessageKeys.BOTSTATUS_FAILED_PLATFORMS_RATIO)
                            .replace("{failed}", String.valueOf(e.deliveryFailed()))
                            .replace("{total}", String.valueOf(e.deliveryTotal()))
                    : t(MessageKeys.BOTSTATUS_FAILED_PLATFORMS);
            lines.add(allFailed ? styles.error(header) : styles.warn(header));
            if (e.deliveryTargets() != null) {
                for (String target : e.deliveryTargets()) {
                    lines.add(allFailed ? styles.error(target) : styles.warn(target));
                }
            }
        }
        appendErrorIfAny(lines, e);
        return joinLines(lines);
    }

    /** WebSocket 详情：连接状态 + 最近错误。 */
    public Component buildWsDetail() {
        HealthStatus.Entry e = health.get("easybot");
        List<Component> lines = new ArrayList<>();
        lines.add(detailLabel(
                "WS",
                e.wsConnected()
                        ? styles.success(t(MessageKeys.BOTSTATUS_STATE_CONNECTED))
                        : styles.error(t(MessageKeys.BOTSTATUS_STATE_DISCONNECTED))));
        appendErrorIfAny(lines, e);
        return joinLines(lines);
    }

    /** 给状态词附加「点击查看详情」交互。 */
    private Component attachDetail(Component word, String label, String command) {
        return word.clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(
                        i18n == null
                                ? "点击查看" + label + "详情"
                                : i18n.msg(
                                        i18n.langFor(),
                                        MessageKeys.BOTSTATUS_HOVER_DETAILS,
                                        java.util.Map.of("label", label)))));
    }

    /**
     * http 状态词：httpOk 绿 / httpUnknown 黄 / httpNotOk 红，文字与颜色同时表达状态。
     * 任一批量投递目标失败（deliveryFailed &gt; 0）也会使 http 显示 httpNotOk。
     */
    private TextComponent httpWord(HealthStatus.Entry e) {
        if (!e.httpChecked()) {
            return styles.warn(t(MessageKeys.BOTSTATUS_STATE_HTTP_UNKNOWN));
        }
        return httpHealthy(e)
                ? styles.success(t(MessageKeys.BOTSTATUS_STATE_HTTP_OK))
                : styles.error(t(MessageKeys.BOTSTATUS_STATE_HTTP_NOT_OK));
    }

    private static boolean httpAbnormal(HealthStatus.Entry e) {
        return !e.httpChecked() || !httpHealthy(e);
    }

    private static boolean httpHealthy(HealthStatus.Entry e) {
        return e.httpOk() && e.deliveryFailed() == 0;
    }

    private Component detailLabel(String label, TextComponent state) {
        return styles.warn(label + ": ").append(state);
    }

    private TextComponent httpState(HealthStatus.Entry e) {
        if (!e.httpChecked()) {
            return styles.warn(t(MessageKeys.BOTSTATUS_STATE_NOT_CHECKED));
        }
        return httpHealthy(e)
                ? styles.success(t(MessageKeys.BOTSTATUS_STATE_HEALTHY))
                : styles.error(t(MessageKeys.BOTSTATUS_STATE_UNHEALTHY));
    }

    private void appendErrorIfAny(List<Component> lines, HealthStatus.Entry e) {
        if (e.lastError() != null && !e.lastError().isEmpty()) {
            lines.add(styles.error(t(MessageKeys.BOTSTATUS_ERROR_PREFIX) + e.lastError()));
        }
    }

    private static Component joinLines(List<Component> lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            result = result.append(lines.get(i));
            if (i < lines.size() - 1) {
                result = result.append(Component.newline());
            }
        }
        return result;
    }
}
