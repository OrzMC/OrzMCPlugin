package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzConfigCommand;
import com.jokerhub.paper.plugin.orzmc.events.OrzBowShootEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzChatEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzCommandGuardEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzDebugEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzExploitHardeningEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzLoginRateLimitEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzMenuEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzPlayerEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzPortalEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzSecurityAuditEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzServerEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzTNTEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzTPEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzWhiteListEvent;
import com.jokerhub.paper.plugin.orzmc.features.chat.ChatSpamFilterEventService;
import com.jokerhub.paper.plugin.orzmc.features.chat.ChatSpamFilterService;
import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.features.menu.MenuCommandService;
import com.jokerhub.paper.plugin.orzmc.features.menu.MenuEventService;
import com.jokerhub.paper.plugin.orzmc.features.player.LoginAccessControlService;
import com.jokerhub.paper.plugin.orzmc.features.player.PlayerEventAggregator;
import com.jokerhub.paper.plugin.orzmc.features.player.PlayerEventService;
import com.jokerhub.paper.plugin.orzmc.features.portal.PortalCommandService;
import com.jokerhub.paper.plugin.orzmc.features.portal.PortalEventService;
import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandAuditService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandGuardEventService;
import com.jokerhub.paper.plugin.orzmc.features.security.ExploitHardeningEventService;
import com.jokerhub.paper.plugin.orzmc.features.security.ExploitHardeningService;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.features.security.LoginRateLimitEventService;
import com.jokerhub.paper.plugin.orzmc.features.security.LoginRateLimitService;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerEventService;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerFeedbackService;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerLifecycleService;
import com.jokerhub.paper.plugin.orzmc.features.server.StartupSecurityAuditService;
import com.jokerhub.paper.plugin.orzmc.features.teleport.EntityTeleportPolicyService;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowEventService;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowService;
import com.jokerhub.paper.plugin.orzmc.features.tnt.TntEventService;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistEventService;
import com.jokerhub.paper.plugin.orzmc.infra.binding.EventBinder;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MainConfig;
import java.util.Arrays;
import org.bukkit.GameMode;
import org.bukkit.event.Listener;

/**
 * 功能模块。
 *
 * <p>将所有 Feature 服务集中创建，并注册 Bukkit 事件监听器和命令。
 * 依赖所有其他模块创建完毕后才构造。</p>
 */
public final class FeatureModule implements ServiceModule {

    private final GeoIpAccessService geoIpAccessService;
    private final AccessRuleService accessRuleService;
    /** 命令审计日志（安全加固 P0-4）：audit/command_audit.log，超限轮转。 */
    private final CommandAuditService commandAuditService;
    /** 危险命令拦截（安全加固 P0-3）：玩家聊天栏 + 控制台命令统一过 guard。 */
    private final CommandGuardEventService commandGuardEventService;

    private final GuideService guideService;
    /** 在线玩家列表格式化（$l 命令与上下线广播共用，rankService 创建后注入）。 */
    private final com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter listFormatter =
            new com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter();

    private final PlayerEventService playerEventService;
    /** 登录访问控制统一入口：维护模式 → IP 黑名单 → 玩家名规则 → GeoIP。 */
    private final LoginAccessControlService loginAccessControlService;

    private final TntEventService tntEventService;
    private final WhitelistEventService whitelistEventService;
    private final MenuEventService menuEventService;
    private final TeleportBowService teleportBowService;
    private final TeleportBowEventService teleportBowEventService;
    private final PortalEventService portalEventService;
    private final ServerFeedbackService serverFeedbackService;
    private final ServerEventService serverEventService;
    private final ServerLifecycleService serverLifecycleService;
    /** 启动安全自检报告（安全加固 P1-2）：ServerLoadEvent 时采集配置 PRIVATE 私信管理员。 */
    private final StartupSecurityAuditService startupSecurityAuditService;
    /** 聊天反垃圾（安全加固 P2-1）：AsyncChatEvent 按玩家限流 + 链接/重复检测。 */
    private final ChatSpamFilterEventService chatSpamFilterEventService;
    /** 进服限流/反 bot（安全加固 P2-2）：AsyncPlayerPreLoginEvent 按 IP 限频率/并发。 */
    private final LoginRateLimitEventService loginRateLimitEventService;
    /** 已知漏洞加固（安全加固 P2-3）：书页上限 / 32k 物品 / 单区块实体上限。 */
    private final ExploitHardeningEventService exploitHardeningEventService;

    private final MenuCommandService menuCommandService;
    private final PortalCommandService portalCommandService;
    private final OrzConfigCommand orzConfigCommand;
    private final com.jokerhub.paper.plugin.orzmc.features.rank.RankService rankService;
    private final com.jokerhub.paper.plugin.orzmc.features.rank.RankCommandService rankCommandService;
    private final com.jokerhub.paper.plugin.orzmc.features.review.ReviewService reviewService;
    private final com.jokerhub.paper.plugin.orzmc.features.review.ReviewCommandService reviewCommandService;
    /** 玩家名颜色服务（按权限等级：头顶/聊天/Tab 三处着色）。 */
    private final com.jokerhub.paper.plugin.orzmc.features.rank.PlayerRankDisplayService rankDisplayService;
    /** 等级变更 → 颜色实时刷新桥（LP 启用时非 null，软依赖条件实例化）。 */
    private final com.jokerhub.paper.plugin.orzmc.features.rank.RankDisplayLpBridge rankDisplayLpBridge;

    // 模块引用（供事件/命令注册使用）
    private final PlatformModule platform;
    private final BotModule botModule;
    private final MaintenanceModule maintenanceModule;
    private final MainConfig mainConfig;
    private final FeatureCommandRegistrar commandRegistrar;

    public FeatureModule(
            PlatformModule platform,
            BotModule botModule,
            PortalModule portalModule,
            MaintenanceModule maintenanceModule) {
        this.mainConfig = MainConfig.from(platform.configService().getConfig("config"));
        // Feature services
        this.geoIpAccessService = new GeoIpAccessService(platform.configs());
        this.accessRuleService = new AccessRuleService(
                platform.configService(), platform.serverFacade().logger());
        // 命令审计 + 危险命令判定核心：由平台模块统一持有（$e 与事件共享同一实例）
        this.commandAuditService = platform.commandAuditService();
        // 危险命令拦截：纯判定核心 + 事件编排；guard 每次读取最新配置（Supplier），/config reload 生效
        this.commandGuardEventService = new CommandGuardEventService(
                platform.commandGuardService(),
                platform.configs(),
                botModule.notifier(),
                new CommandFeedbackService(),
                commandAuditService,
                org.bukkit.Bukkit.getLogger());
        this.guideService = new GuideService(platform.serverFacade(), platform.configService(), platform.textStyles());
        // 上下线通知：窗口聚合限流（不丢消息），单发走原模板、多发走 player_digest 摘要
        this.playerEventService = new PlayerEventService(
                platform.serverFacade(),
                platform.configs(),
                platform.textStyles(),
                botModule.notifier(),
                platform.throttledNotifier(),
                new PlayerEventAggregator(
                        platform.serverFacade(), platform.configs(), botModule.notifier(), this.listFormatter));
        this.loginAccessControlService = new LoginAccessControlService(
                maintenanceModule.worldMaintenanceService(),
                accessRuleService,
                geoIpAccessService,
                playerEventService,
                botModule.notifier(),
                platform.configs(),
                platform.textStyles(),
                platform.serverFacade(),
                platform.throttledNotifier()); // IP 黑名单/玩家名规则拦截私信限频（防重连刷屏打爆 QQ 频控）
        this.tntEventService = new TntEventService(
                platform.configs(), platform.textStyles(), botModule.notifier(), platform.serverScheduler());
        this.whitelistEventService = new WhitelistEventService(
                platform.configs(),
                platform.textStyles(),
                botModule.notifier(),
                platform.throttledNotifier()); // whitelist_block 群通知限频（防刷屏打爆 QQ 频控）
        this.menuEventService = new MenuEventService(platform.textStyles());
        this.teleportBowService = new TeleportBowService(platform.serverFacade(), platform.textStyles());
        this.teleportBowEventService = new TeleportBowEventService(teleportBowService);
        this.portalEventService = new PortalEventService(platform.serverFacade(), portalModule.portalService());
        this.serverFeedbackService =
                new ServerFeedbackService(platform.serverFacade(), platform.configs(), platform.textStyles());
        this.serverEventService = new ServerEventService(
                serverFeedbackService,
                maintenanceModule.worldMaintenanceService(),
                platform.configs(),
                botModule.notifier());
        this.serverLifecycleService =
                new ServerLifecycleService(platform.serverFacade(), platform.configs(), botModule.notifier());
        this.startupSecurityAuditService =
                new StartupSecurityAuditService(platform.serverFacade(), platform.configs(), botModule.notifier());
        // 聊天反垃圾：纯判定核心 + 事件编排；chat 每次读取最新配置（Supplier），/config reload 生效
        this.chatSpamFilterEventService = new ChatSpamFilterEventService(
                new ChatSpamFilterService(platform.configs()::chat), platform.configs(), platform.textStyles());
        // 进服限流：纯判定核心 + 事件编排；login_rate_limit 每次读取最新配置（Supplier），/config reload 生效
        this.loginRateLimitEventService = new LoginRateLimitEventService(
                new LoginRateLimitService(platform.configs()::loginRateLimit),
                platform.configs(),
                botModule.notifier(),
                platform.textStyles());
        // 漏洞加固：纯判定核心 + 事件编排；exploit_hardening 每次读取最新配置（Supplier），/config reload 生效
        this.exploitHardeningEventService = new ExploitHardeningEventService(
                new ExploitHardeningService(platform.configs()::exploitHardening),
                platform.configs(),
                botModule.notifier(),
                platform.textStyles());
        this.menuCommandService = new MenuCommandService(platform.textStyles());
        this.portalCommandService = new PortalCommandService(portalModule.portalService(), platform.textStyles());
        this.orzConfigCommand = new OrzConfigCommand(
                platform.configService(), platform.textStyles(), botModule.botMessageService()::reloadConfig);
        // 权限晋升（Rank）模块：时长（读服务器原生 stats 文件）+ 自动晋升 + 通用审核框架
        // permission.yml 两段式统一存储（config 阈值 / reviews 审核记录；权限组状态由 LP track 持有）
        var permissionStore =
                new com.jokerhub.paper.plugin.orzmc.features.rank.PermissionStore(platform.configService());
        var rankPromoter = createRankPromoter(platform);
        // 通用审核框架：通知端口适配现有 Notifier + 模板；玩家解析端口适配 OfflinePlayer
        var reviewNotifier = new com.jokerhub.paper.plugin.orzmc.infra.notify.ReviewNotifierAdapter(
                platform.configs(), botModule.notifier());
        var playerLookup = new com.jokerhub.paper.plugin.orzmc.infra.player.BukkitPlayerLookup();
        this.rankService = new com.jokerhub.paper.plugin.orzmc.features.rank.RankService(
                permissionStore, rankPromoter, permissionStore.memberThresholdHours(), reviewNotifier);
        // 在线列表格式化注入权限组解析（$l 命令与上下线广播共用，一次注入两处生效）
        this.listFormatter.setRankService(this.rankService);
        this.reviewService = new com.jokerhub.paper.plugin.orzmc.features.review.ReviewService(
                permissionStore, reviewNotifier, playerLookup, platform.serverFacade()::runSync);
        // 注册审核类型：handler 由 rank 模块注入（LP 授权），框架零 LP 依赖。
        // 审核通过 = track 升一级；异步授权（LP 操作在非服务器线程），结果 null/异常 视为
        // 授权失败 → 保持 PENDING 不落 APPROVED（避免「已通过但未生效」漂移）。
        this.reviewService.register(promotionType("builder-promotion", "晋升建造者", "builder", "member"));
        this.reviewService.register(promotionType("admin-promotion", "晋升管理员", "admin", "builder"));
        this.rankCommandService = new com.jokerhub.paper.plugin.orzmc.features.rank.RankCommandService(
                rankService, reviewService, platform.textStyles());
        this.reviewCommandService = new com.jokerhub.paper.plugin.orzmc.features.review.ReviewCommandService(
                reviewService, platform.textStyles());
        // 玩家名颜色（按权限等级）：rankService 创建后装配；LP 启用时桥接等级变更实时刷新。
        // 三元短路：仅 LP 启用时才求值 LuckPermsProvider.get()，LP 缺失时 rankDisplayLpBridge 为 null
        this.rankDisplayService = new com.jokerhub.paper.plugin.orzmc.features.rank.PlayerRankDisplayService(
                platform.serverFacade(), rankService, () -> platform.configs().rankColors());
        this.rankDisplayLpBridge = rankPromoter.isAvailable()
                ? new com.jokerhub.paper.plugin.orzmc.features.rank.RankDisplayLpBridge(
                        platform.serverFacade().plugin(),
                        platform.serverFacade(),
                        net.luckperms.api.LuckPermsProvider.get(),
                        rankDisplayService)
                : null;

        // 保留模块引用（供事件/命令注册使用）
        this.platform = platform;
        this.botModule = botModule;
        this.maintenanceModule = maintenanceModule;
        this.commandRegistrar = new FeatureCommandRegistrar(
                platform,
                botModule,
                guideService,
                menuCommandService,
                teleportBowService,
                portalCommandService,
                accessRuleService,
                reviewCommandService,
                rankCommandService,
                rankService,
                orzConfigCommand);
    }

    /** 构造一个「晋升」审核类型（builder-promotion / admin-promotion 共用模板，消除重复）。 */
    private com.jokerhub.paper.plugin.orzmc.features.review.ReviewType promotionType(
            String id, String name, String targetGroup, String fromGroup) {
        return new com.jokerhub.paper.plugin.orzmc.features.review.ReviewType(
                id,
                name,
                targetGroup,
                rawArgs -> {
                    var data = new java.util.LinkedHashMap<String, String>();
                    data.put("target-group", targetGroup);
                    if (rawArgs != null && !rawArgs.isBlank()) {
                        data.put("reason", rawArgs);
                    }
                    return data;
                },
                playerId -> rankService.currentGroup(playerId).equals(fromGroup),
                data -> "申请" + name
                        + (data.get("reason") == null || data.get("reason").isBlank() ? "" : "：" + data.get("reason")),
                playerId -> rankService.promoteAsync(playerId).thenApply(to -> to != null));
    }

    // --- Event Listener Registration ---

    public void setupEventListeners(OrzMC plugin) {
        Listener[] eventListeners = new Listener[] {
            new OrzBowShootEvent(plugin, teleportBowEventService),
            new OrzPlayerEvent(plugin, loginAccessControlService, guideService, playerEventService),
            new OrzTPEvent(
                    plugin,
                    platform.serverFacade(),
                    // entityTeleportEnabled=true 表示「允许所有实体传送」，
                    // service 的 cancelEnabled 需取反（true = 仅白名单内实体可传送）
                    new EntityTeleportPolicyService(
                            !mainConfig.entityTeleportEnabled(), mainConfig.entityTeleportWhitelist())),
            new OrzTNTEvent(plugin, tntEventService),
            new OrzCommandGuardEvent(plugin, commandGuardEventService),
            new OrzChatEvent(plugin, chatSpamFilterEventService),
            new OrzLoginRateLimitEvent(plugin, loginRateLimitEventService),
            new OrzExploitHardeningEvent(plugin, exploitHardeningEventService),
            new OrzMenuEvent(plugin, menuEventService),
            new OrzServerEvent(plugin, serverEventService),
            new OrzSecurityAuditEvent(plugin, startupSecurityAuditService),
            new OrzWhiteListEvent(plugin, whitelistEventService),
            new OrzDebugEvent(plugin, botModule.botInboundHandler()),
            new OrzPortalEvent(plugin, portalEventService),
            new com.jokerhub.paper.plugin.orzmc.events.OrzRankEvent(plugin, rankService),
            new com.jokerhub.paper.plugin.orzmc.events.OrzRankDisplayEvent(plugin, rankDisplayService)
        };
        EventBinder.bind(plugin, Arrays.asList(eventListeners));
        // 周期自愈：约 60s 重刷一次在线玩家颜色（兜底 Paper 头顶名刷新遗漏 + 配置改动兜底生效）
        rankDisplayService.startPeriodicRefresh();
        // rank_colors.* 运行时改动（/orzmc config set/reset/reload）→ 立即重刷在线玩家，消除最长 ~60s 生效延迟；
        // 命令线程 → 调度线程（Folia 区域线程安全，PlayerRankDisplayService 的 applyTo 必须在调度线程执行）
        orzConfigCommand.setRankColorsReload(
                () -> platform.serverFacade().runSync(rankDisplayService::refreshAllOnline));
        // access_rules 运行时改动（/orzmc config reload）→ 刷新 AccessRuleService 内存缓存，
        // 手动编辑 access_rules.yml 后即改即生效（无需重启）
        orzConfigCommand.setAccessRulesReload(accessRuleService::reload);
        // command_policies.* 运行时改动（/orzmc config set/reset/reload）→ 刷新命令拦截器策略快照，
        // 即改即生效且热路径不再全量重解析
        orzConfigCommand.setCommandPoliciesReload(commandRegistrar::refreshCommandPolicies);
    }

    // --- Command Registration ---

    public void setupCommandHandlers(OrzMC plugin) {
        commandRegistrar.registerCommands(plugin);
    }

    // --- Whitelist ---

    public void enableForceWhitelist(OrzMC plugin) {
        boolean forceWhitelist = false;
        try {
            forceWhitelist = platform.configs().whitelist().forceWhitelist();
        } catch (Exception e) {
            plugin.getLogger().warning("读取 forceWhitelist 配置失败: " + e.getMessage());
        }
        plugin.getServer().setWhitelist(forceWhitelist);
        plugin.getServer().setWhitelistEnforced(forceWhitelist);
        plugin.getServer().reloadWhitelist();
        plugin.getServer().setDefaultGameMode(GameMode.SURVIVAL);
        if (forceWhitelist) {
            plugin.getLogger().info("服务端使用强制白名单机制");
        }
    }

    // --- Getters for cross-module references ---

    public AccessRuleService accessRuleService() {
        return accessRuleService;
    }

    public com.jokerhub.paper.plugin.orzmc.features.review.ReviewService reviewService() {
        return reviewService;
    }

    public com.jokerhub.paper.plugin.orzmc.features.rank.RankService rankService() {
        return rankService;
    }

    /** 玩家名颜色服务（按权限等级三处着色，跨模块引用用）。 */
    public com.jokerhub.paper.plugin.orzmc.features.rank.PlayerRankDisplayService rankDisplayService() {
        return rankDisplayService;
    }

    /** 在线列表格式化器（单一事实源，$l/$w 命令与上下线广播共享）。 */
    public com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter listFormatter() {
        return listFormatter;
    }

    // --- Lifecycle ---

    /** 禁用/重载时同步冲刷上下线聚合批次，避免最后一个窗口的事件被调度器取消而静默丢弃。 */
    public void flushPlayerNotifications() {
        playerEventService.flushPending();
    }

    public void notifyServerStop() {
        serverLifecycleService.notifyServerStop();
    }

    /**
     * 创建权限执行器（软依赖条件实例化）。
     *
     * <p>LP 已启用 → 实例化 {@code LuckPermsPromoter}（直接引用 LP API 类型，此时
     * LP 插件提供 API 类，类加载安全）；LP 未启用 → 改用 {@code NoopRankPromoter}
     * 降级。关键：LP 未启用时<b>永不执行</b> {@code new LuckPermsPromoter}，
     * JVM 不会加载该类，因此不会因缺失 LP API 类而 NoClassDefFoundError。</p>
     */
    private com.jokerhub.paper.plugin.orzmc.features.rank.RankPromoter createRankPromoter(
            com.jokerhub.paper.plugin.orzmc.assembly.PlatformModule platform) {
        com.jokerhub.paper.plugin.orzmc.features.rank.PlayerNameResolver resolver = playerId -> {
            // 离线服：UUID→名字，玩家可能不在线（审核时申请者已退出），用 OfflinePlayer 查缓存
            return org.bukkit.Bukkit.getOfflinePlayer(playerId).getName();
        };
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            // 装即用：启动自动补齐 track「rank」+ 四级组骨架（幂等，已有不覆盖）
            new com.jokerhub.paper.plugin.orzmc.features.rank.LuckPermsBootstrap(
                            net.luckperms.api.LuckPermsProvider.get(), org.bukkit.Bukkit.getLogger())
                    .initialize();
            // asyncExecutor = 服务器异步调度器：LP 升降级（含 loadUser/saveUser 等待）在
            // 非服务器线程执行，杜绝「调度线程同步等待 LP future」自锁（Folia LP 适配器行为）
            return new com.jokerhub.paper.plugin.orzmc.features.rank.LuckPermsPromoter(
                    resolver, platform.serverFacade()::runSync, platform.serverFacade()::runAsync);
        }
        org.bukkit.Bukkit.getLogger().warning("[OrzMC] 未检测到 LuckPerms，权限管理功能不可用（时长查询/申请记录仍可用）");
        return new com.jokerhub.paper.plugin.orzmc.features.rank.NoopRankPromoter();
    }
}
