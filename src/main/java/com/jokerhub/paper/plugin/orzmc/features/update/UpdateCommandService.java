package com.jokerhub.paper.plugin.orzmc.features.update;

import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
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

    public UpdateCommandService(ServerFacade server, UpdateService updates, OrzTextStyles styles) {
        this.server = server;
        this.updates = updates;
        this.styles = styles;
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
            return styles.error("检查更新失败：" + err.getMessage());
        }
        if (outcome == null) {
            return styles.error("检查更新失败，请查看控制台日志");
        }
        return switch (outcome.state()) {
            case CHECK_FAILED -> styles.error("检查更新失败，请查看控制台日志");
            case UNKNOWN_LOCAL ->
                styles.error(
                        "无法识别当前运行版本（构建信息缺失）；Hangar 最新版本 " + outcome.latest().version());
            case AVAILABLE -> styles.success("发现新版本 " + outcome.latest().version() + "。执行 /update now 下载，重启服务器后生效");
            case UP_TO_DATE ->
                styles.info(
                        outcome.latest() == null
                                ? "当前通道无可用版本信息"
                                : "已是最新版本 " + outcome.latest().version() + "（本地 " + updates.currentVersion() + "）");
        };
    }

    private Component describeDownload(UpdateService.DownloadOutcome outcome, Throwable err) {
        if (err != null) {
            return styles.error("下载失败：" + err.getMessage());
        }
        if (outcome == null) {
            return styles.error("下载失败，请查看控制台日志");
        }
        return switch (outcome.state()) {
            case DOWNLOADED -> styles.success("新版本已下载到 " + outcome.detail() + "，重启服务器后生效");
            case ALREADY_DOWNLOADED -> styles.info(outcome.detail());
            case NO_UPDATE -> styles.info(outcome.detail());
            case BUSY -> styles.warn(outcome.detail());
            case FAILED -> styles.error("下载失败：" + outcome.detail());
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
