package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * $b/$o 地图备份/优化命令处理器（从 BotCommandService 抽离）。
 *
 * <p>{@code maintenanceService} 通过 {@link Supplier} 注入——组合根经
 * {@link BotCommandService#injectDependencies} 一次性注入，处理器调用时读取最新值；未注入时静默忽略（向后兼容测试）。</p>
 */
final class MaintenanceCommandHandler extends BotCommandContext {

    private final Supplier<WorldMaintenanceService> maintenanceService;

    MaintenanceCommandHandler(
            ServerFacade server, TypedConfigProvider configs, Supplier<WorldMaintenanceService> maintenanceService) {
        super(server, configs);
        this.maintenanceService = maintenanceService;
    }

    void handleBackup(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        MaintenanceConfig maintenance = configs.maintenance();
        long tickTimeThreshold = maintenance.optimizeTickTimeThreshold();
        int retain = maintenance.backupRetentionCount();
        WorldMaintenanceService svc = maintenanceService.get();
        if (svc != null) {
            svc.backup(tickTimeThreshold, retain, msg -> emit(callback, "command_backup", Map.of("message", msg), msg));
        }
    }

    void handleOptimize(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        if (!guardOptimizeEnabled(callback)) return;
        MaintenanceConfig maintenance = configs.maintenance();
        long tickTimeThreshold = maintenance.optimizeTickTimeThreshold();
        WorldMaintenanceService svc = maintenanceService.get();
        if (svc != null) {
            svc.optimize(tickTimeThreshold, msg -> emit(callback, "command_optimize", Map.of("message", msg), msg));
        }
    }
}
