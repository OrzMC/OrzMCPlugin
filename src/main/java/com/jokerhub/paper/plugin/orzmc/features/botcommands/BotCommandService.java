package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistService;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.paging.Paginator;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateRenderer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class BotCommandService implements BotInboundHandler {
    private final BotCommandFeedbackService feedbackService = new BotCommandFeedbackService();
    private final BotCommandListFeedbackService listFeedbackService = new BotCommandListFeedbackService();
    private final ConfigService configService;
    private final OrzTextStyles styles;
    private Notifier notifier;

    public BotCommandService(ConfigService configService, OrzTextStyles styles) {
        this.configService = configService;
        this.styles = styles;
    }

    public void setNotifier(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public void handleMessage(String message, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        parse(message, isAdmin, callback);
    }

    public void parse(String message, Boolean isAdmin, Consumer<MessageEnvelope> callback) {
        if (!OrzUserCmd.isValidCmd(message)) return;
        FileConfiguration templatesCfg = configService.getConfig("templates");

        ArrayList<String> cmd = new ArrayList<>(Arrays.asList(message.split("[, ]+")));
        String cmdString = cmd.remove(0);
        Set<String> userNameSet = new HashSet<>(cmd);

        if (cmdString.equals(OrzUserCmd.SHOW_PLAYERS.getCmdString())) {
            onlinePlayersInfo(callback, templatesCfg);
        } else if (cmdString.equals(OrzUserCmd.SHOW_WHITELIST.getCmdString())) {
            Integer page = null;
            if (!cmd.isEmpty()) {
                String token = cmd.get(0);
                try {
                    page = Integer.parseInt(token);
                } catch (Exception ignored) {
                }
            }
            whiteListInfo(callback, templatesCfg, page, isAdmin);
        } else if (cmdString.equals(OrzUserCmd.SHOW_HELP.getCmdString())) {
            String help = feedbackService.helpInfo();
            emit(callback, templatesCfg, "command_help", Map.of("help", help), help);
        } else if (cmdString.equals(OrzUserCmd.ADD_PLAYER_TO_WHITELIST.getCmdString())) {
            addWhiteListInfo(isAdmin, userNameSet, callback, templatesCfg);
        } else if (cmdString.equals(OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST.getCmdString())) {
            removeWhiteListInfo(isAdmin, userNameSet, callback, templatesCfg);
        } else if (cmdString.equals(OrzUserCmd.BACKUP.getCmdString())) {
            backupWorld(isAdmin, callback, templatesCfg);
        } else if (cmdString.equals(OrzUserCmd.OPTIMIZE_WORLD.getCmdString())) {
            optimizeWorld(isAdmin, callback, templatesCfg);
        } else {
            String help = feedbackService.helpInfo();
            emit(callback, templatesCfg, "command_help", Map.of("help", help), help);
        }
    }

    private void onlinePlayersInfo(Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg) {
        OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
            ArrayList<Player> onlinePlayers = listFeedbackService.currentOnlinePlayers();
            BotCommandListFeedbackService.OnlineList online = listFeedbackService.buildOnlineList(
                    templatesCfg, onlinePlayers, OrzMC.server().getMaxPlayers());
            emit(callback, templatesCfg, "command_players", listFeedbackService.onlineVars(online), online.fallback());
        });
    }

    private void whiteListInfo(
            Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg, Integer page, boolean isAdmin) {
        OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
            FileConfiguration wlCfg = configService.getConfig("whitelist");
            WhitelistService svc = WhitelistService.defaultImpl();
            int delayTicks = Math.max(0, wlCfg.getInt("pagination_delay_ticks", 5));
            if (isAdmin) {
                renderWhitelistWithCleanup(callback, templatesCfg, page, delayTicks, svc, wlCfg);
            } else {
                renderWhitelistPages(callback, templatesCfg, page, delayTicks, svc);
            }
        });
    }

    private void renderWhitelistWithCleanup(
            Consumer<MessageEnvelope> callback,
            FileConfiguration templatesCfg,
            Integer page,
            int delayTicks,
            WhitelistService svc,
            FileConfiguration wlCfg) {
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
            java.util.Set<String> removed =
                    svc.cleanupInactivePlayers(OrzMC.server(), Math.max(1, wlCfg.getInt("cleanup_inactive_days", 90)));
            OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
                ArrayList<String> updatedLines = new ArrayList<>(svc.buildWhitelistLines(OrzMC.server()));
                BotCommandListFeedbackService.WhitelistHeader updatedHeaderInfo =
                        listFeedbackService.buildWhitelistHeader(templatesCfg, updatedLines.size());
                if (!removed.isEmpty()) {
                    BotCommandListFeedbackService.CleanupNotice cleanupNotice =
                            listFeedbackService.buildCleanupNotice(templatesCfg, removed);
                    emitWhitelistCleanup(callback, templatesCfg, cleanupNotice);
                }
                emitWhitelistPages(callback, templatesCfg, updatedHeaderInfo.header(), updatedLines, delayTicks, page);
            });
        });
    }

    private void renderWhitelistPages(
            Consumer<MessageEnvelope> callback,
            FileConfiguration templatesCfg,
            Integer page,
            int delayTicks,
            WhitelistService svc) {
        ArrayList<String> lines = new ArrayList<>(svc.buildWhitelistLines(OrzMC.server()));
        BotCommandListFeedbackService.WhitelistHeader headerInfo =
                listFeedbackService.buildWhitelistHeader(templatesCfg, lines.size());
        emitWhitelistPages(callback, templatesCfg, headerInfo.header(), lines, delayTicks, page);
    }

    private void addWhiteListInfo(
            boolean isAdmin,
            Set<String> userNames,
            Consumer<MessageEnvelope> callback,
            FileConfiguration templatesCfg) {
        if (!guardWhitelistCommand(OrzUserCmd.ADD_PLAYER_TO_WHITELIST, isAdmin, userNames, callback, templatesCfg)) {
            return;
        }
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
            WhitelistService svc = WhitelistService.defaultImpl();
            String message = svc.addPlayers(OrzMC.server(), userNames);
            emitWhitelistAddResult(callback, templatesCfg, message);
        });
    }

    private void removeWhiteListInfo(
            boolean isAdmin,
            Set<String> userNames,
            Consumer<MessageEnvelope> callback,
            FileConfiguration templatesCfg) {
        if (!guardWhitelistCommand(
                OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST, isAdmin, userNames, callback, templatesCfg)) {
            return;
        }
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
            WhitelistService svc = WhitelistService.defaultImpl();
            String message = svc.removePlayers(OrzMC.server(), userNames);
            emitWhitelistRemoveResult(callback, templatesCfg, message);
        });
    }

    private void backupWorld(boolean isAdmin, Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg) {
        if (!guardAdminCommand(OrzUserCmd.BACKUP, isAdmin, callback, templatesCfg)) {
            return;
        }
        long tickTimeThreshold = configService.getConfig("maintenance").getLong("optimize_tick_time_threshold", 300L);
        int retain = configService.getConfig("maintenance").getInt("backup_retention_count", 10);
        WorldMaintenanceService svc = new WorldMaintenanceService(configService, styles, notifier);
        svc.backup(tickTimeThreshold, retain, msg -> emitBackup(callback, templatesCfg, msg));
    }

    private void optimizeWorld(boolean isAdmin, Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg) {
        if (!guardAdminCommand(OrzUserCmd.OPTIMIZE_WORLD, isAdmin, callback, templatesCfg)) {
            return;
        }
        if (!guardOptimizeEnabled(callback, templatesCfg)) {
            return;
        }
        long tickTimeThreshold = configService.getConfig("maintenance").getLong("optimize_tick_time_threshold", 300L);
        WorldMaintenanceService svc = new WorldMaintenanceService(configService, styles, notifier);
        svc.optimize(tickTimeThreshold, msg -> emitOptimize(callback, templatesCfg, msg));
    }

    private void emitWhitelistCleanup(
            Consumer<MessageEnvelope> callback,
            FileConfiguration templatesCfg,
            BotCommandListFeedbackService.CleanupNotice notice) {
        emit(
                callback,
                templatesCfg,
                "command_whitelist_cleanup",
                listFeedbackService.cleanupVars(notice),
                notice.fallback());
    }

    private void emitWhitelistPage(
            Consumer<MessageEnvelope> callback,
            FileConfiguration templatesCfg,
            BotCommandListFeedbackService.WhitelistPage pageInfo) {
        emit(callback, templatesCfg, "command_whitelist_page", pageInfo.vars(), pageInfo.fallback());
    }

    private void emitWhitelistAddResult(
            Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg, String message) {
        emit(callback, templatesCfg, "command_whitelist_add_result", Map.of("message", message), message);
    }

    private void emitWhitelistRemoveResult(
            Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg, String message) {
        emit(callback, templatesCfg, "command_whitelist_remove_result", Map.of("message", message), message);
    }

    private void emitBackup(Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg, String message) {
        emit(callback, templatesCfg, "command_backup", Map.of("message", message), message);
    }

    private void emitOptimize(Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg, String message) {
        emit(callback, templatesCfg, "command_optimize", Map.of("message", message), message);
    }

    private void emitOptimizeDisabled(
            Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg, String message) {
        emit(callback, templatesCfg, "command_optimize_disabled", Map.of("message", message), message);
    }

    private boolean guardOptimizeEnabled(Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg) {
        boolean enabled = false;
        try {
            enabled = configService.getConfig("maintenance").getBoolean("optimize_enabled");
        } catch (Exception ignored) {
        }
        if (!enabled) {
            emitOptimizeDisabled(callback, templatesCfg, "地图优化功能已禁用");
            return false;
        }
        return true;
    }

    private boolean guardAdminCommand(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg) {
        if (isAdmin) {
            return true;
        }
        emitAdminRequired(callback, templatesCfg, feedbackService.adminRequiredTip(cmd));
        return false;
    }

    private boolean guardWhitelistCommand(
            OrzUserCmd cmd,
            boolean isAdmin,
            Set<String> userNames,
            Consumer<MessageEnvelope> callback,
            FileConfiguration templatesCfg) {
        if (!isAdmin) {
            emitAdminRequired(callback, templatesCfg, feedbackService.adminRequiredTip(cmd));
            return false;
        }
        if (userNames.isEmpty()) {
            emitUsage(callback, templatesCfg, feedbackService.usageTip(cmd));
            return false;
        }
        return true;
    }

    private void emitWhitelistPages(
            Consumer<MessageEnvelope> callback,
            FileConfiguration templatesCfg,
            String header,
            ArrayList<String> lines,
            int delayTicks,
            Integer page) {
        Paginator.paginatePages(
                (pageIndex, total, headerText, body) -> {
                    BotCommandListFeedbackService.WhitelistPage pageInfo =
                            listFeedbackService.buildWhitelistPage(templatesCfg, headerText, pageIndex, total, body);
                    emitWhitelistPage(callback, templatesCfg, pageInfo);
                },
                header,
                lines,
                delayTicks,
                page);
    }

    private void emitAdminRequired(Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg, String tip) {
        emit(callback, templatesCfg, "command_admin_required", Map.of("message", tip), tip);
    }

    private void emitUsage(Consumer<MessageEnvelope> callback, FileConfiguration templatesCfg, String tip) {
        emit(callback, templatesCfg, "command_usage", Map.of("message", tip), tip);
    }

    private void emit(
            Consumer<MessageEnvelope> callback,
            FileConfiguration templatesCfg,
            String templateKey,
            Map<String, String> vars,
            String fallback) {
        String template = TemplateRenderer.resolveTemplate(templateKey, templatesCfg, fallback);
        MessageEnvelope env = TemplateRenderer.renderEnvelope(templateKey, template, vars, templatesCfg);
        callback.accept(env);
    }
}
