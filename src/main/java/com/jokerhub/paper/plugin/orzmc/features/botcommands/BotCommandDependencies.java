package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewService;
import com.jokerhub.paper.plugin.orzmc.features.security.BlacklistService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandAuditService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandGuardService;
import com.jokerhub.paper.plugin.orzmc.infra.logging.LogCaptureService;
import com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter;

/**
 * {@link BotCommandService} 的跨模块依赖集合（六边形边界处的一次性批量注入）。
 *
 * <p>取代原先的 6 个 {@code setXxxService} 二阶段 setter——组合根在 {@link BotCommandService}
 * 构造后、WebSocket 连接前一次性 {@code injectDependencies(this)}，消除「先连上、后注入」的
 * 半初始化窗口。字段可空：未注入的依赖由对应处理器降级处理（见各 Handler 的 Supplier 说明）。</p>
 */
public final class BotCommandDependencies {

    private WorldMaintenanceService maintenanceService;
    private BlacklistService blacklistService;
    private ReviewService reviewService;
    private RankService rankService;
    private LogCaptureService logCaptureService;
    private CommandGuardService commandGuardService;
    private CommandAuditService commandAuditService;
    private OnlineListFormatter listFormatter;

    public BotCommandDependencies maintenanceService(WorldMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
        return this;
    }

    public BotCommandDependencies blacklistService(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
        return this;
    }

    public BotCommandDependencies reviewService(ReviewService reviewService) {
        this.reviewService = reviewService;
        return this;
    }

    public BotCommandDependencies rankService(RankService rankService) {
        this.rankService = rankService;
        return this;
    }

    public BotCommandDependencies logCaptureService(LogCaptureService logCaptureService) {
        this.logCaptureService = logCaptureService;
        return this;
    }

    public BotCommandDependencies commandGuardService(CommandGuardService commandGuardService) {
        this.commandGuardService = commandGuardService;
        return this;
    }

    public BotCommandDependencies commandAuditService(CommandAuditService commandAuditService) {
        this.commandAuditService = commandAuditService;
        return this;
    }

    public BotCommandDependencies listFormatter(OnlineListFormatter listFormatter) {
        this.listFormatter = listFormatter;
        return this;
    }

    WorldMaintenanceService maintenanceService() {
        return maintenanceService;
    }

    BlacklistService blacklistService() {
        return blacklistService;
    }

    ReviewService reviewService() {
        return reviewService;
    }

    RankService rankService() {
        return rankService;
    }

    LogCaptureService logCaptureService() {
        return logCaptureService;
    }

    CommandGuardService commandGuardService() {
        return commandGuardService;
    }

    CommandAuditService commandAuditService() {
        return commandAuditService;
    }

    OnlineListFormatter listFormatter() {
        return listFormatter;
    }
}
