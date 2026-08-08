package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.Format;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.TargetType;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
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
        when(configs.renderTemplate(anyString(), anyMap(), anyString()))
                .thenReturn(new MessageEnvelope(TargetType.PUBLIC, "response", Format.DEFAULT));
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
        service.setReviewService(reviewService);
        when(reviewService.reviewByApplicantName(eq("TestMember"), eq(true), anyString()))
                .thenReturn(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.Result.ok("已通过", "r1"));

        service.parse("$v y TestMember", true, callback);
        verify(reviewService).reviewByApplicantName("TestMember", true, "群管理员");
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewApprove_withSenderName_passesSenderAsReviewer() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.setReviewService(reviewService);
        when(reviewService.reviewByApplicantName(eq("TestMember"), eq(true), anyString()))
                .thenReturn(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.Result.ok("已通过", "r1"));

        // 网关透传发送者身份 → 审核人记真实昵称（非硬编码「群管理员」）
        service.parse("$v y TestMember", true, "老板", callback);
        verify(reviewService).reviewByApplicantName("TestMember", true, "老板");
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewReject_byPlayerName_callsReviewByApplicantName() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.setReviewService(reviewService);
        when(reviewService.reviewByApplicantName(eq("TestMember"), eq(false), anyString()))
                .thenReturn(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.Result.ok("已拒绝", "r1"));

        service.parse("$v n TestMember", true, callback);
        verify(reviewService).reviewByApplicantName("TestMember", false, "群管理员");
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewApprove_nonAdmin_doesNotCallReview() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.setReviewService(reviewService);

        service.parse("$v y TestMember", false, callback);
        verify(reviewService, never()).reviewByApplicantName(anyString(), anyBoolean(), anyString());
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewList_callsListPending() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.setReviewService(reviewService);
        when(reviewService.listPending()).thenReturn(java.util.List.of());

        service.parse("$v l", true, callback);
        verify(reviewService).listPending();
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_reviewUnknownSubcommand_emitsUsage() {
        var reviewService = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.class);
        service.setReviewService(reviewService);

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
        service.setReviewService(reviewService);
        var type = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewType.class);
        when(reviewService.typeById("builder-promotion")).thenReturn(java.util.Optional.of(type));
        var request = mock(com.jokerhub.paper.plugin.orzmc.features.review.ReviewRequest.class);
        when(request.id()).thenReturn("req-1");
        when(reviewService.pendingFor("builder-promotion", "TestMember")).thenReturn(java.util.Optional.of(request));
        when(reviewService.review(eq("req-1"), eq(true), anyString()))
                .thenReturn(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.Result.ok("已通过", "r1"));

        service.parse("$v y builder-promotion TestMember", true, callback);
        verify(reviewService).review(anyString(), eq(true), eq("群管理员"));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_permissionDemote_playerName_callsDemote() {
        var rankService = mock(RankService.class);
        service.setRankService(rankService);
        when(rankService.isLuckPermsAvailable()).thenReturn(true);
        when(rankService.resolvePlayerId("TestMember")).thenReturn(java.util.UUID.randomUUID());
        when(rankService.demote(any(java.util.UUID.class))).thenReturn("member");

        service.parse("$p d TestMember", true, callback);
        verify(rankService).demote(any(java.util.UUID.class));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_permissionUpgrade_playerName_callsPromote() {
        var rankService = mock(RankService.class);
        service.setRankService(rankService);
        when(rankService.isLuckPermsAvailable()).thenReturn(true);
        when(rankService.resolvePlayerId("TestMember")).thenReturn(java.util.UUID.randomUUID());
        when(rankService.promote(any(java.util.UUID.class))).thenReturn("builder");

        service.parse("$p u TestMember", true, callback);
        verify(rankService).promote(any(java.util.UUID.class));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_permissionDemote_atBottom_emitsError() {
        var rankService = mock(RankService.class);
        service.setRankService(rankService);
        when(rankService.isLuckPermsAvailable()).thenReturn(true);
        when(rankService.resolvePlayerId("TestPlayer")).thenReturn(java.util.UUID.randomUUID());
        when(rankService.demote(any(java.util.UUID.class))).thenReturn(null); // 链底 no-op

        service.parse("$p d TestPlayer", true, callback);
        verify(rankService).demote(any(java.util.UUID.class));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_permissionDemote_unknownPlayer_emitsError() {
        var rankService = mock(RankService.class);
        service.setRankService(rankService);
        when(rankService.resolvePlayerId("Nobody")).thenReturn(null);

        service.parse("$p d Nobody", true, callback);
        verify(rankService, never()).demote(any(java.util.UUID.class));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }

    @Test
    void parse_permissionDemote_nonAdmin_doesNotCallDemote() {
        var rankService = mock(RankService.class);
        service.setRankService(rankService);

        service.parse("$p d TestMember", false, callback);
        verify(rankService, never()).demote(any(java.util.UUID.class));
        verify(callback, atLeastOnce()).accept(any(MessageEnvelope.class));
    }
}
