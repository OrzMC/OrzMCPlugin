package com.jokerhub.paper.plugin.orzmc.features.update;

import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /update check|now} 命令的服务逻辑（与 Brigadier 注册解耦，便于测试）。
 *
 * <p>命令语义：{@code check} 只查不下载；{@code now} 检查并下载到 plugins/update（无论
 * {@code update.auto_download} 配置）。结果经异步完成后回投给发送者——玩家经其实体调度器
 * 回发（Folia 区域线程安全），控制台经 global region 回发。</p>
 */
public final class UpdateCommandService {

    private final ServerFacade server;
    private final UpdateService updates;
    private final OrzTextStyles styles;
    private final I18nService i18n;

    public UpdateCommandService(ServerFacade server, UpdateService updates, OrzTextStyles styles, I18nService i18n) {
        this.server = server;
        this.updates = updates;
        this.styles = styles;
        this.i18n = i18n;
    }

    /** /update 为管理命令：状态文案按 default_lang（R1）决议。 */
    private String m(String key, Map<String, String> vars) {
        return i18n.msg(i18n.langFor(), key, vars);
    }

    /** /update check：只查询当前通道是否有新版本。 */
    public void check(CommandSender sender) {
        updates.check().whenComplete((outcome, err) -> reply(sender, describeCheck(outcome, err)));
    }

    /** /update now：检查并下载新版本到 plugins/update（重启后生效）。 */
    public void downloadNow(CommandSender sender) {
        updates.downloadNow().whenComplete((outcome, err) -> reply(sender, describeDownload(outcome, err)));
    }

    private Component describeCheck(UpdateService.CheckOutcome outcome, Throwable err) {
        if (err != null) {
            return styles.error(
                    m(MessageKeys.CMD_UPDATE_CHECK_FAILED_REASON, Map.of("reason", String.valueOf(err.getMessage()))));
        }
        if (outcome == null) {
            return styles.error(m(MessageKeys.CMD_UPDATE_CHECK_FAILED, Map.of()));
        }
        return switch (outcome.state()) {
            case CHECK_FAILED -> styles.error(m(MessageKeys.CMD_UPDATE_CHECK_FAILED, Map.of()));
            case UNKNOWN_LOCAL ->
                styles.error(m(
                        MessageKeys.CMD_UPDATE_UNKNOWN_LOCAL,
                        Map.of("version", String.valueOf(outcome.latest().version()))));
            case AVAILABLE ->
                styles.success(m(
                        MessageKeys.CMD_UPDATE_AVAILABLE,
                        Map.of("version", String.valueOf(outcome.latest().version()))));
            case UP_TO_DATE ->
                styles.info(
                        outcome.latest() == null
                                ? m(MessageKeys.CMD_UPDATE_NO_CHANNEL_INFO, Map.of())
                                : m(
                                        MessageKeys.CMD_UPDATE_UP_TO_DATE,
                                        Map.of(
                                                "version",
                                                        String.valueOf(
                                                                outcome.latest().version()),
                                                "current", String.valueOf(updates.currentVersion()))));
        };
    }

    private Component describeDownload(UpdateService.DownloadOutcome outcome, Throwable err) {
        if (err != null) {
            return styles.error(m(
                    MessageKeys.CMD_UPDATE_DOWNLOAD_FAILED_REASON, Map.of("reason", String.valueOf(err.getMessage()))));
        }
        if (outcome == null) {
            return styles.error(m(MessageKeys.CMD_UPDATE_DOWNLOAD_FAILED, Map.of()));
        }
        return switch (outcome.state()) {
            case DOWNLOADED ->
                styles.success(m(MessageKeys.CMD_UPDATE_DOWNLOADED, Map.of("path", String.valueOf(outcome.detail()))));
            case ALREADY_DOWNLOADED -> styles.info(m(MessageKeys.CMD_UPDATE_ALREADY_DOWNLOADED, Map.of()));
            case NO_UPDATE -> styles.info(m(MessageKeys.CMD_UPDATE_NO_UPDATE, Map.of()));
            case BUSY -> styles.warn(m(MessageKeys.CMD_UPDATE_BUSY, Map.of()));
            case FAILED ->
                styles.error(m(
                        MessageKeys.CMD_UPDATE_DOWNLOAD_FAILED_REASON,
                        Map.of("reason", String.valueOf(outcome.detail()))));
        };
    }

    private void reply(CommandSender sender, Component message) {
        if (sender instanceof Player player) {
            player.getScheduler().run(server.plugin(), task -> player.sendMessage(message), () -> {});
        } else {
            server.runSync(() -> sender.sendMessage(message));
        }
    }
}
