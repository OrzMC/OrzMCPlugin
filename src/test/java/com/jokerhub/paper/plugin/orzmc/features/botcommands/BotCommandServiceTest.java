package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.Format;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.TargetType;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandAuditService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandGuardService;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRule;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.SecurityGuardConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.logging.LogCaptureService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.testutil.TestI18n;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BotCommandServiceTest {

    private ServerFacade serverFacade;
    private TypedConfigProvider configs;
    private BotCommandService service;
    private Consumer<MessageEnvelope> callback;
    private Logger logger;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        serverFacade = mock(ServerFacade.class);
        configs = mock(TypedConfigProvider.class);
        callback = mock(Consumer.class);
        logger = mock(Logger.class);

        BotConfig botConfig = new BotConfig("$", null, null);
        WhitelistConfig whitelistConfig = mock(WhitelistConfig.class);
        when(configs.bot()).thenReturn(botConfig);
        when(configs.whitelist()).thenReturn(whitelistConfig);
        when(configs.renderTemplate(anyString(), anyMap(), anyString())).thenAnswer(invocation -> {
            // 透传 fallback 文本进信封，便于断言实际展示内容（如帮助文本与 usageTip 一致）
            String fallback = invocation.getArgument(2);
            return new MessageEnvelope(TargetType.PUBLIC, fallback, Format.DEFAULT);
        });
        when(serverFacade.logger()).thenReturn(logger);

        // Execute async/sync runnables immediately
        doAnswer(invocation -> {
                    Runnable r = invocation.getArgument(0);
                    r.run();
                    return null;
                })
                .when(serverFacade)
                .runAsync(any(Runnable.class));

        doAnswer(invocation -> {
                    Runnable r = invocation.getArgument(0);
                    r.run();
                    return null;
                })
                .when(serverFacade)
                .runSync(any(Runnable.class));

        doAnswer(invocation -> {
                    Runnable r = invocation.getArgument(0);
                    r.run();
                    return null;
                })
                .when(serverFacade)
                .runLater(any(Runnable.class), anyLong());

        service = new BotCommandService(serverFacade, configs);
    }

    // ---- parse: message routing ----

    @Test
    void parse_nonMatchingPrefix_doesNotCallCallback() {
        service.parse("hello world", false, callback);
        verify(callback, never()).accept(any());
    }

    @Test
    void parse_showHelp_emitsHelp() {
        service.parse("$help", false, callback);
        verify(callback).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_unknownCommand_emitsHelp() {
        service.parse("$unknown", false, callback);
        verify(callback).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_addToWhitelist_nonAdmin_emitsAdminRequired() {
        service.parse("$a Alice", false, callback);
        verify(callback).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_addToWhitelist_adminEmptyNames_emitsUsage() {
        service.parse("$a", true, callback);
        verify(callback).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_addToWhitelist_admin_emitsResult() {
        var botServer = mock(org.bukkit.Server.class);
        when(serverFacade.server()).thenReturn(botServer);
        when(botServer.getWhitelistedPlayers()).thenReturn(java.util.Set.of());
        when(botServer.getOfflinePlayer(anyString())).thenReturn(mock(org.bukkit.OfflinePlayer.class));

        service.parse("$a Alice", true, callback);
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_removeFromWhitelist_admin_emitsResult() {
        var botServer = mock(org.bukkit.Server.class);
        when(serverFacade.server()).thenReturn(botServer);
        when(botServer.getWhitelistedPlayers()).thenReturn(java.util.Set.of());
        when(botServer.getOfflinePlayer(anyString())).thenReturn(mock(org.bukkit.OfflinePlayer.class));

        service.parse("$r Alice", true, callback);
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_showWhitelist_nonAdmin_emitsWhitelistLines() {
        var botServer = mock(org.bukkit.Server.class);
        when(serverFacade.server()).thenReturn(botServer);
        when(botServer.getWhitelistedPlayers()).thenReturn(java.util.Set.of());

        service.parse("$w", false, callback);
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_showPlayers_emitsOnlineList() {
        var botServer = mock(org.bukkit.Server.class);
        when(serverFacade.server()).thenReturn(botServer);
        when(botServer.getOnlinePlayers()).thenReturn(java.util.Set.of());
        when(botServer.getMaxPlayers()).thenReturn(20);

        service.parse("$o", false, callback);
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_executeConsole_nonAdmin_emitsAdminRequired() {
        service.parse("$e say hello", false, callback);
        verify(callback).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_executeConsole_adminBlankCommand_emitsUsage() {
        service.parse("$e", true, callback);
        verify(callback).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_executeConsole_adminWithCommand_emitsOutput() {
        when(serverFacade.executeConsoleCommand("say hello"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("say hello", true, java.util.List.of("执行成功")));

        service.parse("$e say hello", true, callback);
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    // ---- $e 日志窗口收集（setLogCaptureService 注入后） ----

    @Test
    void parse_executeConsole_withLogCapture_emitsAssembledOutput() {
        LogCaptureService capture = mock(LogCaptureService.class);
        when(capture.watermark()).thenReturn(42L);
        when(capture.drainSince(42L)).thenReturn(java.util.List.of("async log line"));
        service.injectDependencies(new BotCommandDependencies().logCaptureService(capture));

        when(serverFacade.executeConsoleCommand("say hello"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("say hello", true, java.util.List.of("sync line")));

        service.parse("$e say hello", true, callback);

        org.mockito.ArgumentCaptor<MessageEnvelope> captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        // 同步捕获行在前，日志窗口行在后（合并去重）
        assertEquals("sync line\nasync log line", captor.getValue().message());
    }

    @Test
    void parse_executeConsole_withLogCapture_watermarkTakenBeforeDispatch() {
        LogCaptureService capture = mock(LogCaptureService.class);
        when(capture.watermark()).thenReturn(1L);
        when(capture.drainSince(anyLong())).thenReturn(java.util.List.of());
        service.injectDependencies(new BotCommandDependencies().logCaptureService(capture));

        when(serverFacade.executeConsoleCommand("say hi"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("say hi", true, java.util.List.of()));

        service.parse("$e say hi", true, callback);

        // 水位必须在执行命令前取，否则命令自身的日志行会漏出窗口
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(capture, serverFacade);
        inOrder.verify(capture).watermark();
        inOrder.verify(serverFacade).executeConsoleCommand("say hi");
    }

    @Test
    void parse_executeConsole_withLogCapture_noOutputFallsBackToStatus() {
        LogCaptureService capture = mock(LogCaptureService.class);
        when(capture.watermark()).thenReturn(7L);
        when(capture.drainSince(7L)).thenReturn(java.util.List.of());
        service.injectDependencies(new BotCommandDependencies().logCaptureService(capture));

        when(serverFacade.executeConsoleCommand("say hi"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("say hi", true, java.util.List.of()));

        service.parse("$e say hi", true, callback);

        org.mockito.ArgumentCaptor<MessageEnvelope> captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("命令已执行: say hi", captor.getValue().message());
    }

    @Test
    void parse_executeConsole_withLogCapture_filtersIssuedServerCommandNoise() {
        LogCaptureService capture = mock(LogCaptureService.class);
        when(capture.watermark()).thenReturn(9L);
        when(capture.drainSince(9L))
                .thenReturn(java.util.List.of("Rcon issued server command: /say hi", "real async output"));
        service.injectDependencies(new BotCommandDependencies().logCaptureService(capture));

        when(serverFacade.executeConsoleCommand("say hi"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("say hi", true, java.util.List.of()));

        service.parse("$e say hi", true, callback);

        org.mockito.ArgumentCaptor<MessageEnvelope> captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("real async output", captor.getValue().message());
    }

    @Test
    void parse_executeConsole_withLogCapture_bufferOverflow_prependsWarning() {
        LogCaptureService capture = mock(LogCaptureService.class);
        when(capture.watermark()).thenReturn(3L);
        when(capture.drainSince(3L)).thenReturn(java.util.List.of("survived line"));
        when(capture.hasGapSince(3L)).thenReturn(true);
        service.injectDependencies(new BotCommandDependencies().logCaptureService(capture));

        when(serverFacade.executeConsoleCommand("say hi"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("say hi", true, java.util.List.of()));

        service.parse("$e say hi", true, callback);

        org.mockito.ArgumentCaptor<MessageEnvelope> captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("⚠️ 日志缓冲溢出，输出可能不完整\nsurvived line", captor.getValue().message());
    }

    // ---- $e 危险命令 guard（安全加固 P0-5） ----

    private static CommandGuardService defaultGuard() {
        return new CommandGuardService(
                () -> new SecurityGuardConfig(true, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, true, true),
                TestI18n.newService());
    }

    @Test
    void parse_executeConsole_guardBlocked_doesNotExecuteAndEmitsReason() {
        CommandAuditService audit = mock(CommandAuditService.class);
        service.injectDependencies(
                new BotCommandDependencies().commandGuardService(defaultGuard()).commandAuditService(audit));

        service.parse("$e op steve", true, "老板", callback);

        verify(serverFacade, never()).executeConsoleCommand(anyString());
        verify(audit).record(CommandAuditService.SOURCE_BOT, "老板", "op steve", true);
        org.mockito.ArgumentCaptor<MessageEnvelope> captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertTrue(captor.getValue().message().contains("已被安全拦截"));
    }

    @Test
    void parse_executeConsole_guardAllowed_lifecycleCommandExecutes() {
        // 运维生命周期命令（stop/reload 等）不再默认拦截：$e stop 可正常执行停服
        CommandAuditService audit = mock(CommandAuditService.class);
        service.injectDependencies(
                new BotCommandDependencies().commandGuardService(defaultGuard()).commandAuditService(audit));
        when(serverFacade.executeConsoleCommand("stop"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("stop", true, java.util.List.of("执行成功")));

        service.parse("$e stop", true, "老板", callback);

        verify(serverFacade).executeConsoleCommand("stop");
        verify(audit).record(CommandAuditService.SOURCE_BOT, "老板", "stop", false);
    }

    @Test
    void parse_executeConsole_guardAllowed_executesAndAudits() {
        CommandAuditService audit = mock(CommandAuditService.class);
        service.injectDependencies(
                new BotCommandDependencies().commandGuardService(defaultGuard()).commandAuditService(audit));
        when(serverFacade.executeConsoleCommand("say hi"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("say hi", true, java.util.List.of("执行成功")));

        service.parse("$e say hi", true, "老板", callback);

        verify(serverFacade).executeConsoleCommand("say hi");
        verify(audit).record(CommandAuditService.SOURCE_BOT, "老板", "say hi", false);
    }

    @Test
    void parse_executeConsole_guardWarn_stillExecutesWithAudit() {
        CommandAuditService audit = mock(CommandAuditService.class);
        service.injectDependencies(
                new BotCommandDependencies().commandGuardService(defaultGuard()).commandAuditService(audit));
        when(serverFacade.executeConsoleCommand("kill @e"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("kill @e", true, java.util.List.of("执行成功")));

        service.parse("$e kill @e", true, "老板", callback);

        verify(serverFacade).executeConsoleCommand("kill @e");
        verify(audit).record(CommandAuditService.SOURCE_BOT, "老板", "kill @e", false);
    }

    @Test
    void parse_executeConsole_guardDisabled_executesWithoutBlock() {
        CommandAuditService audit = mock(CommandAuditService.class);
        service.injectDependencies(new BotCommandDependencies()
                .commandGuardService(new CommandGuardService(
                        () -> new SecurityGuardConfig(false, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, true, true),
                        TestI18n.newService()))
                .commandAuditService(audit));
        when(serverFacade.executeConsoleCommand("stop"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("stop", true, java.util.List.of("执行成功")));

        service.parse("$e stop", true, "老板", callback);

        verify(serverFacade).executeConsoleCommand("stop");
        verify(audit).record(CommandAuditService.SOURCE_BOT, "老板", "stop", false);
    }

    @Test
    void parse_botConfigException_usesDefaults() {
        when(configs.bot()).thenThrow(new RuntimeException("config error"));

        service.parse("$help", false, callback);
        verify(callback).accept(any(MessageEnvelope.class));
    }

    // ---- extractArgs (private, via reflection) ----

    @Test
    void extractCommandArgs_shorterThanCmd_returnsEmpty() {
        assertEquals("", invokeExtractArgs("$e", "$e"));
    }

    @Test
    void extractCommandArgs_sameLength_returnsEmpty() {
        assertEquals("", invokeExtractArgs("$e ", "$e"));
    }

    @Test
    void extractCommandArgs_withArgs_returnsTrimmedArgs() {
        assertEquals("say hello", invokeExtractArgs("$e say hello", "$e"));
    }

    // ---- matchesCommandPrefix (private, via reflection) ----

    @Test
    void matchesCommandPrefix_exactMatch_returnsTrue() {
        assertTrue(invokeMatchesPrefix("$o", "$o"));
    }

    @Test
    void matchesCommandPrefix_partialMatchWithoutSpace_returnsFalse() {
        assertFalse(invokeMatchesPrefix("$other", "$o"));
    }

    @Test
    void matchesCommandPrefix_partialMatchWithSpace_returnsTrue() {
        assertTrue(invokeMatchesPrefix("$o Alice", "$o"));
    }

    @Test
    void matchesCommandPrefix_shorterMessage_returnsFalse() {
        assertFalse(invokeMatchesPrefix("$", "$o"));
    }

    // ---- reflection helpers ----

    private boolean invokeMatchesPrefix(String message, String cmd) {
        try {
            java.lang.reflect.Method m =
                    BotCommandService.class.getDeclaredMethod("matchesCommandPrefix", String.class, String.class);
            m.setAccessible(true);
            return (boolean) m.invoke(service, message, cmd);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeExtractArgs(String rawMessage, String fullCmd) {
        try {
            java.lang.reflect.Method m =
                    BotCommandService.class.getDeclaredMethod("extractArgs", String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(service, rawMessage, fullCmd);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- $v review command (admin) ----

    @Test
    void parse_reviewApprove_byPlayerName_callsReviewByApplicantName() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.injectDependencies(new BotCommandDependencies().reviewService(reviewService));
        when(reviewService.reviewByApplicantName(eq("TestMember"), eq(true), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.Result.ok("已通过", "r1")));

        service.parse("$v y TestMember", true, callback);
        verify(reviewService).reviewByApplicantName("TestMember", true, "群管理员");
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewApprove_withSenderName_passesSenderAsReviewer() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.injectDependencies(new BotCommandDependencies().reviewService(reviewService));
        when(reviewService.reviewByApplicantName(eq("TestMember"), eq(true), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.Result.ok("已通过", "r1")));

        // 网关透传发送者身份 → 审核人记真实昵称（非硬编码「群管理员」）
        service.parse("$v y TestMember", true, "老板", callback);
        verify(reviewService).reviewByApplicantName("TestMember", true, "老板");
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewReject_byPlayerName_callsReviewByApplicantName() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.injectDependencies(new BotCommandDependencies().reviewService(reviewService));
        when(reviewService.reviewByApplicantName(eq("TestMember"), eq(false), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.Result.ok("已拒绝", "r1")));

        service.parse("$v n TestMember", true, callback);
        verify(reviewService).reviewByApplicantName("TestMember", false, "群管理员");
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewApprove_nonAdmin_doesNotCallReview() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.injectDependencies(new BotCommandDependencies().reviewService(reviewService));

        service.parse("$v y TestMember", false, callback);
        verify(reviewService, never()).reviewByApplicantName(anyString(), anyBoolean(), anyString());
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewList_callsListPending() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.injectDependencies(new BotCommandDependencies().reviewService(reviewService));
        when(reviewService.listPending()).thenReturn(java.util.List.of());

        service.parse("$v l", true, callback);
        verify(reviewService).listPending();
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewUnknownSubcommand_emitsUsage() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.injectDependencies(new BotCommandDependencies().reviewService(reviewService));

        service.parse("$v x", true, callback);
        verify(reviewService, never()).reviewByApplicantName(anyString(), anyBoolean(), anyString());
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewNoService_emitsError() {
        service.parse("$v l", true, callback);
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewApprove_byTypeAndPlayer_callsReviewById() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.injectDependencies(new BotCommandDependencies().reviewService(reviewService));
        var type = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewType.class);
        when(reviewService.typeById("builder-promotion")).thenReturn(java.util.Optional.of(type));
        var request = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewRequest.class);
        when(request.id()).thenReturn("req-1");
        when(reviewService.pendingFor("builder-promotion", "TestMember")).thenReturn(java.util.Optional.of(request));
        when(reviewService.review(eq("req-1"), eq(true), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.Result.ok("已通过", "r1")));

        service.parse("$v y builder-promotion TestMember", true, callback);
        verify(reviewService).review(anyString(), eq(true), eq("群管理员"));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_permissionDemote_playerName_callsDemote() {
        var rankService = mock(RankService.class);
        service.injectDependencies(new BotCommandDependencies().rankService(rankService));
        when(rankService.isLuckPermsAvailable()).thenReturn(true);
        when(rankService.resolvePlayerId("TestMember")).thenReturn(java.util.UUID.randomUUID());
        when(rankService.demoteAsync(any(java.util.UUID.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture("member"));

        service.parse("$p d TestMember", true, callback);
        verify(rankService).demoteAsync(any(java.util.UUID.class));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_permissionUpgrade_playerName_callsPromote() {
        var rankService = mock(RankService.class);
        service.injectDependencies(new BotCommandDependencies().rankService(rankService));
        when(rankService.isLuckPermsAvailable()).thenReturn(true);
        when(rankService.resolvePlayerId("TestMember")).thenReturn(java.util.UUID.randomUUID());
        when(rankService.promoteAsync(any(java.util.UUID.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture("builder"));

        service.parse("$p u TestMember", true, callback);
        verify(rankService).promoteAsync(any(java.util.UUID.class));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_permissionDemote_atBottom_emitsError() {
        var rankService = mock(RankService.class);
        service.injectDependencies(new BotCommandDependencies().rankService(rankService));
        when(rankService.isLuckPermsAvailable()).thenReturn(true);
        when(rankService.resolvePlayerId("TestPlayer")).thenReturn(java.util.UUID.randomUUID());
        when(rankService.demoteAsync(any(java.util.UUID.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null)); // 链底 no-op

        service.parse("$p d TestPlayer", true, callback);
        verify(rankService).demoteAsync(any(java.util.UUID.class));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_permissionDemote_unknownPlayer_emitsError() {
        var rankService = mock(RankService.class);
        service.injectDependencies(new BotCommandDependencies().rankService(rankService));
        when(rankService.resolvePlayerId("Nobody")).thenReturn(null);

        service.parse("$p d Nobody", true, callback);
        verify(rankService, never()).demoteAsync(any(java.util.UUID.class));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_permissionDemote_nonAdmin_doesNotCallDemote() {
        var rankService = mock(RankService.class);
        service.injectDependencies(new BotCommandDependencies().rankService(rankService));

        service.parse("$p d TestMember", false, callback);
        verify(rankService, never()).demoteAsync(any(java.util.UUID.class));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    // ---- parse: $cmd ? 帮助拦截与 fallback 收敛（PR #12 审查 M1/M2 回归护栏） ----

    @Test
    void parse_backupHelpQuery_emitsUsageWithoutBackup() {
        var maintenance = mock(com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService.class);
        service.injectDependencies(new BotCommandDependencies().maintenanceService(maintenance));

        service.parse("$b ?", true, callback);
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals(
                new BotCommandFeedbackService().usageTip(OrzUserCmd.BACKUP, "$"),
                captor.getValue().message());
        verify(maintenance, never()).backup(anyLong(), anyInt(), any());
    }

    @Test
    void parse_backupHelpQuerySuffix_emitsUsageWithoutBackup() {
        // M1：前缀匹配——$b ?x / $b ?? / $b ? 2 均视为帮助请求，绝不执行备份
        var maintenance = mock(com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService.class);
        service.injectDependencies(new BotCommandDependencies().maintenanceService(maintenance));

        service.parse("$b ? 2", true, callback);
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals(
                new BotCommandFeedbackService().usageTip(OrzUserCmd.BACKUP, "$"),
                captor.getValue().message());
        verify(maintenance, never()).backup(anyLong(), anyInt(), any());
    }

    @Test
    void parse_backupHelpQueryFullWidthSpace_emitsUsageWithoutBackup() {
        // M1：U+3000 全角空格分隔（Java trim 不去全角空格），归一化后仍触发帮助拦截
        var maintenance = mock(com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService.class);
        service.injectDependencies(new BotCommandDependencies().maintenanceService(maintenance));

        service.parse("$b　?", true, callback);
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals(
                new BotCommandFeedbackService().usageTip(OrzUserCmd.BACKUP, "$"),
                captor.getValue().message());
        verify(maintenance, never()).backup(anyLong(), anyInt(), any());
    }

    @Test
    void parse_optimizeHelpQuery_emitsUsageWithoutOptimize() {
        var maintenance = mock(com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService.class);
        service.injectDependencies(new BotCommandDependencies().maintenanceService(maintenance));

        service.parse("$o ?", true, callback);
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals(
                new BotCommandFeedbackService().usageTip(OrzUserCmd.OPTIMIZE_WORLD, "$"),
                captor.getValue().message());
        verify(maintenance, never()).optimize(anyLong(), any());
    }

    @Test
    void parse_permissionBlank_emitsUsageMatchingTip() {
        var rankService = mock(RankService.class);
        service.injectDependencies(new BotCommandDependencies().rankService(rankService));
        when(rankService.isLuckPermsAvailable()).thenReturn(true);

        service.parse("$p", true, callback);
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals(
                new BotCommandFeedbackService().usageTip(OrzUserCmd.PERMISSION, "$"),
                captor.getValue().message());
    }

    @Test
    void parse_permissionInvalidSubcommand_emitsUsageMatchingTip() {
        var rankService = mock(RankService.class);
        service.injectDependencies(new BotCommandDependencies().rankService(rankService));
        when(rankService.isLuckPermsAvailable()).thenReturn(true);

        service.parse("$p x", true, callback);
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals(
                new BotCommandFeedbackService().usageTip(OrzUserCmd.PERMISSION, "$"),
                captor.getValue().message());
    }

    @Test
    void parse_reviewBlank_emitsUsageMatchingTip() {
        service.injectDependencies(new BotCommandDependencies()
                .reviewService(mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class)));

        service.parse("$v", true, callback);
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals(
                new BotCommandFeedbackService().usageTip(OrzUserCmd.REVIEW, "$"),
                captor.getValue().message());
    }

    @Test
    void parse_addWhitelistBlank_emitsUsageMatchingTip() {
        service.parse("$a", true, callback);
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals(
                new BotCommandFeedbackService().usageTip(OrzUserCmd.ADD_PLAYER_TO_WHITELIST, "$"),
                captor.getValue().message());
    }

    @Test
    void parse_executeConsoleHelpQuery_emitsUsage() {
        service.parse("$e ?", true, callback);
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals(
                new BotCommandFeedbackService().usageTip(OrzUserCmd.EXECUTE_CONSOLE_COMMAND, "$"),
                captor.getValue().message());
    }

    @Test
    void parse_executeConsoleQuestionMarkCommand_notHelp() {
        // M1 $e 特判：控制台命令以 ? 开头（如 "$e ?list"）不算帮助请求，正常执行
        when(serverFacade.executeConsoleCommand("?list"))
                .thenReturn(new ServerFacade.ConsoleCommandResult("?list", true, java.util.List.of()));

        service.parse("$e ?list", true, callback);
        verify(serverFacade).executeConsoleCommand("?list");
    }

    @Test
    void parse_blacklistPlayerRule_callsAccessRuleService() {
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d player prefix bot_", true, callback);

        verify(accessRuleService).addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "bot_");
    }

    @Test
    void parse_blacklistRemovePlayerRule_callsAccessRuleService() {
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d -player suffix _test", true, callback);

        verify(accessRuleService).removePlayerNameRule(PlayerNameRule.MatchType.SUFFIX, "_test");
    }

    @Test
    void parse_blacklistBareRemovePlayer_emitsUsageNotIpRemoval() {
        // $d -player 缺参 → 用法错误，绝不把 "player" 当 IP 黑名单移除
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d -player", true, callback);

        verify(accessRuleService, never()).removeIpPattern(anyString());
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("用法: $d -player <type> <value>", captor.getValue().message());
    }

    @Test
    void parse_blacklistMalformedPlayerPrefix_emitsUsageNotIpRemoval() {
        // $d -playerX / $d playerX 是畸形玩家名规则命令，不应落入 IP 黑名单分支
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d -playerX", true, callback);

        verify(accessRuleService, never()).removeIpPattern(anyString());
        verify(callback).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_blacklistPlayerRule_uppercaseKeyword_addsNameRuleNotIp() {
        // P2：玩家名关键字大小写不敏感——$d Player exact foo 不得被当成 IP 规则误加
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d Player exact foo", true, callback);

        verify(accessRuleService).addPlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo");
        verify(accessRuleService, never()).addIpPattern(anyString());
    }

    @Test
    void parse_blacklistRemovePlayerRule_uppercaseKeyword_removesNameRuleNotIp() {
        // P2：$d -PLAYER ... 同样大小写不敏感
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d -PLAYER suffix _test", true, callback);

        verify(accessRuleService).removePlayerNameRule(PlayerNameRule.MatchType.SUFFIX, "_test");
        verify(accessRuleService, never()).removeIpPattern(anyString());
    }

    @Test
    void parse_blacklistUppercasePlayerList_listsNameRules() {
        // P2：$d Player List 命中列表分支而非 IP 简写
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d Player List", true, callback);

        verify(accessRuleService, never()).addIpPattern(anyString());
        verify(accessRuleService, never()).removeIpPattern(anyString());
        verify(callback).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_blacklistMixedCaseMalformedPlayer_emitsUsageNotIpRemoval() {
        // P2：$d PlayerX 大小写归一后命中 player 前缀分支 → 用法错误，绝不误加 IP "PlayerX"
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d PlayerX", true, callback);

        verify(accessRuleService, never()).addIpPattern(anyString());
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("用法: $d player <type> <value>", captor.getValue().message());
    }

    @Test
    void parse_blacklistIpShorthand_matchTypeKeyword_emitsUsageNotIpAddition() {
        // P2：$d prefix bot_ 首词是匹配类型 → 提示玩家名规则用法，绝不把 "prefix bot_" 当 IP 误加
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d prefix bot_", true, callback);

        verify(accessRuleService, never()).addIpPattern(anyString());
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("玩家名规则请使用: $d player <type> <value>", captor.getValue().message());
    }

    @Test
    void parse_blacklistIpRemoveShorthand_matchTypeKeyword_emitsUsageNotIpRemoval() {
        // P2：$d -exact Steve 同理不落入 IP 移除分支
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d -exact Steve", true, callback);

        verify(accessRuleService, never()).removeIpPattern(anyString());
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("玩家名规则请使用: $d -player <type> <value>", captor.getValue().message());
    }

    @Test
    void parse_blacklistDashEmpty_emitsUsageNotRemoval() {
        // P3：$d - 空模式 → 用法提示，不执行任何移除
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d -", true, callback);

        verify(accessRuleService, never()).removeIpPattern(anyString());
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertTrue(
                captor.getValue().message().contains("用法"),
                "Expected usage hint, got: " + captor.getValue().message());
    }

    @Test
    void parse_blacklistDashSpaceMatchType_emitsUsageNotIpRemoval() {
        // P3：$d - exact foo（破折号后带空格）trim 后首词是匹配类型 → 玩家名规则提示，不落入 IP 移除
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d - exact foo", true, callback);

        verify(accessRuleService, never()).removeIpPattern(anyString());
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("玩家名规则请使用: $d -player <type> <value>", captor.getValue().message());
    }

    @Test
    void parse_blacklistDashSpaceIp_removes() {
        // P3：$d - 1.2.3.4（破折号后带空格）trim 后正常移除 IP
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        when(accessRuleService.removeIpPattern("1.2.3.4")).thenReturn(true);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d - 1.2.3.4", true, callback);

        verify(accessRuleService).removeIpPattern("1.2.3.4");
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("已移除: 1.2.3.4", captor.getValue().message());
    }

    @Test
    void parse_blacklistIpRemove_missing_emitsNotFoundNotRemoved() {
        // P3：移除不存在的 IP → 回「未找到」，不再假报「已移除」
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        when(accessRuleService.removeIpPattern("1.2.3.4")).thenReturn(false);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d -1.2.3.4", true, callback);

        verify(accessRuleService).removeIpPattern("1.2.3.4");
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("未在黑名单中找到: 1.2.3.4", captor.getValue().message());
    }

    @Test
    void parse_blacklistIpRemove_present_emitsRemoved() {
        // P3：确实移除 → 仍报「已移除」
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        when(accessRuleService.removeIpPattern("1.2.3.4")).thenReturn(true);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d -1.2.3.4", true, callback);

        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("已移除: 1.2.3.4", captor.getValue().message());
    }

    @Test
    void parse_blacklistRemovePlayerRule_missing_emitsNotFound() {
        // P3：移除不存在的玩家名规则 → 回「未找到」，不再无条件报「已移除」
        AccessRuleService accessRuleService = mock(AccessRuleService.class);
        when(accessRuleService.removePlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo"))
                .thenReturn(false);
        service.injectDependencies(new BotCommandDependencies().accessRuleService(accessRuleService));

        service.parse("$d -player exact foo", true, callback);

        verify(accessRuleService).removePlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo");
        var captor = org.mockito.ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(callback).accept(captor.capture());
        assertEquals("未找到该玩家名规则: exact:foo", captor.getValue().message());
    }
}
