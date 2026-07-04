package com.jokerhub.paper.plugin.orzmc.infra.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade.ConsoleCommandResult;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ServerFacadeTest {

    private JavaPlugin plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private Logger logger;
    private ServerFacade facade;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        logger = mock(Logger.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getName()).thenReturn("orzmc");
        facade = new ServerFacade(plugin);
    }

    @Test
    void plugin_returnsPlugin() {
        assertSame(plugin, facade.plugin());
    }

    @Test
    void server_returnsServer() {
        assertSame(server, facade.server());
    }

    @Test
    void logger_returnsLogger() {
        assertSame(logger, facade.logger());
    }

    // key() test skipped: NamespacedKey(Plugin, String) requires
    // a running Bukkit server environment (getPluginMeta())

    @Test
    void runSync_delegatesToScheduler() {
        Runnable task = mock(Runnable.class);
        facade.runSync(task);
        verify(scheduler).runTask(plugin, task);
    }

    @Test
    void runAsync_delegatesToScheduler() {
        Runnable task = mock(Runnable.class);
        facade.runAsync(task);
        verify(scheduler).runTaskAsynchronously(plugin, task);
    }

    @Test
    void runLater_delegatesToScheduler() {
        Runnable task = mock(Runnable.class);
        facade.runLater(task, 30L);
        verify(scheduler).runTaskLater(plugin, task, 30L);
    }

    @Test
    void executeConsoleCommands_dispatchesCommandsAndRunsAfter() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        Runnable after = mock(Runnable.class);

        facade.executeConsoleCommands(after, "/cmd1", "cmd2");

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), captor.capture());

        Runnable task = captor.getValue();
        task.run();

        verify(server).dispatchCommand(console, "/cmd1");
        verify(server).dispatchCommand(console, "cmd2");
        verify(after).run();
    }

    @Test
    void executeConsoleCommands_withoutAfter() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);

        facade.executeConsoleCommands(null, "/cmd");

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), captor.capture());

        Runnable task = captor.getValue();
        task.run();

        verify(server).dispatchCommand(console, "/cmd");
    }

    @Test
    void executeConsoleCommand_capturesSendMessageOutput() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(any(ConsoleCommandSender.class), anyString()))
                .thenAnswer(invocation -> {
                    ConsoleCommandSender sender = invocation.getArgument(0);
                    sender.sendMessage("output line 1");
                    sender.sendMessage("output line 2");
                    return true;
                });

        ConsoleCommandResult result = facade.executeConsoleCommand("/say hello");

        assertTrue(result.dispatched());
        assertEquals("say hello", result.command());
        assertTrue(result.outputLines().contains("output line 1"));
        assertTrue(result.outputLines().contains("output line 2"));
    }

    @Test
    void executeConsoleCommand_capturesComponentAndStringArrayMessages() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(any(ConsoleCommandSender.class), anyString()))
                .thenAnswer(invocation -> {
                    ConsoleCommandSender sender = invocation.getArgument(0);
                    sender.sendMessage(Component.text("component message"));
                    sender.sendMessage(new String[] {"multi", "line"});
                    return true;
                });

        ConsoleCommandResult result = facade.executeConsoleCommand("test");

        assertTrue(result.outputLines().contains("component message"));
        assertTrue(result.outputLines().contains("multi"));
        assertTrue(result.outputLines().contains("line"));
    }

    @Test
    void executeConsoleCommand_normalizesLeadingSlash() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(any(ConsoleCommandSender.class), anyString()))
                .thenReturn(true);

        facade.executeConsoleCommand("/normalize me");

        verify(server).dispatchCommand(any(ConsoleCommandSender.class), eq("normalize me"));
    }

    @Test
    void executeConsoleCommand_handlesNullString() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);

        facade.executeConsoleCommand(null);

        verify(server).dispatchCommand(any(ConsoleCommandSender.class), eq(""));
    }

    @Test
    void executeConsoleCommand_handlesBlankString() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);

        facade.executeConsoleCommand("   ");

        verify(server).dispatchCommand(any(ConsoleCommandSender.class), eq(""));
    }

    @Test
    void consoleCommandResult_message_withOutputLines() {
        ConsoleCommandResult result = new ConsoleCommandResult("test", true, List.of("line1", "line2"));
        assertEquals("line1\nline2", result.message());
    }

    @Test
    void consoleCommandResult_message_dispatchedNoOutput() {
        ConsoleCommandResult result = new ConsoleCommandResult("test", true, List.of());
        assertEquals("命令已执行: test", result.message());
    }

    @Test
    void consoleCommandResult_message_notDispatchedNoOutput() {
        ConsoleCommandResult result = new ConsoleCommandResult("test", false, List.of());
        assertEquals("命令不存在或执行失败: test", result.message());
    }

    @Test
    void consoleCommandResult_outputLinesIsImmutable() {
        List<String> mutable = new java.util.ArrayList<>(List.of("a"));
        ConsoleCommandResult result = new ConsoleCommandResult("test", true, mutable);
        assertThrows(
                UnsupportedOperationException.class, () -> result.outputLines().add("b"));
    }

    @Test
    void proxy_handlesSendRichMessage() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(any(ConsoleCommandSender.class), anyString()))
                .thenAnswer(invocation -> {
                    ConsoleCommandSender sender = invocation.getArgument(0);
                    sender.sendRichMessage("<red>rich message</red>");
                    return true;
                });

        ConsoleCommandResult result = facade.executeConsoleCommand("cmd");

        assertEquals(1, result.outputLines().size());
        assertTrue(result.outputLines().get(0).contains("rich message"));
    }

    @Test
    void proxy_handlesSendPlainMessage() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(any(ConsoleCommandSender.class), anyString()))
                .thenAnswer(invocation -> {
                    ConsoleCommandSender sender = invocation.getArgument(0);
                    sender.sendPlainMessage("plain text");
                    return true;
                });

        ConsoleCommandResult result = facade.executeConsoleCommand("cmd");

        assertEquals(1, result.outputLines().size());
        assertEquals("plain text", result.outputLines().get(0));
    }

    @Test
    void proxy_delegatesOtherMethods() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(any(ConsoleCommandSender.class), anyString()))
                .thenReturn(true);

        ConsoleCommandResult result = facade.executeConsoleCommand("cmd");

        assertTrue(result.dispatched());
    }

    @Test
    void addCapturedText_splitsOnNewlines() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(any(ConsoleCommandSender.class), anyString()))
                .thenAnswer(invocation -> {
                    ConsoleCommandSender sender = invocation.getArgument(0);
                    sender.sendMessage("line1\nline2\nline3");
                    return true;
                });

        ConsoleCommandResult result = facade.executeConsoleCommand("cmd");

        assertEquals(3, result.outputLines().size());
        assertEquals("line1", result.outputLines().get(0));
        assertEquals("line2", result.outputLines().get(1));
        assertEquals("line3", result.outputLines().get(2));
    }

    @Test
    void addCapturedText_handlesCarriageReturnNewline() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(any(ConsoleCommandSender.class), anyString()))
                .thenAnswer(invocation -> {
                    ConsoleCommandSender sender = invocation.getArgument(0);
                    sender.sendMessage("line1\r\nline2\r\nline3");
                    return true;
                });

        ConsoleCommandResult result = facade.executeConsoleCommand("cmd");

        assertEquals(3, result.outputLines().size());
    }

    @Test
    void addCapturedText_skipsBlankLines() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(any(ConsoleCommandSender.class), anyString()))
                .thenAnswer(invocation -> {
                    ConsoleCommandSender sender = invocation.getArgument(0);
                    sender.sendMessage("  \ncontent\n  ");
                    return true;
                });

        ConsoleCommandResult result = facade.executeConsoleCommand("cmd");

        // blank lines should be stripped
        assertEquals(1, result.outputLines().size());
        assertEquals("content", result.outputLines().get(0));
    }
}
