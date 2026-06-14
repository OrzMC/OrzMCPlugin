package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.Format;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.TargetType;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
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

        BotConfig botConfig = new BotConfig("$", null, null, null);
        WhitelistConfig whitelistConfig = mock(WhitelistConfig.class);
        when(configs.bot()).thenReturn(botConfig);
        when(configs.whitelist()).thenReturn(whitelistConfig);
        when(configs.renderTemplate(anyString(), anyMap(), anyString()))
                .thenReturn(new MessageEnvelope(TargetType.PUBLIC, "response", null, Format.DEFAULT));
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
}
