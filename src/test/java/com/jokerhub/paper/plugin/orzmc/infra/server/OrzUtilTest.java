package com.jokerhub.paper.plugin.orzmc.infra.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrzUtilTest {

    @Test
    void successText_delegatesToStyles() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        TextComponent expected = mock(TextComponent.class);
        when(styles.success("ok")).thenReturn(expected);

        TextComponent result = OrzUtil.successText(styles, "ok");
        assertSame(expected, result);
        verify(styles).success("ok");
    }

    @Test
    void failureText_delegatesToStyles() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        TextComponent expected = mock(TextComponent.class);
        when(styles.error("fail")).thenReturn(expected);

        TextComponent result = OrzUtil.failureText(styles, "fail");
        assertSame(expected, result);
        verify(styles).error("fail");
    }

    @Test
    void warningText_delegatesToStyles() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        TextComponent expected = mock(TextComponent.class);
        when(styles.warn("caution")).thenReturn(expected);

        TextComponent result = OrzUtil.warningText(styles, "caution");
        assertSame(expected, result);
        verify(styles).warn("caution");
    }

    @Test
    void executeConsoleCmd_dispatchesCommandsAndRunsTask() {
        ServerFacade server = mock(ServerFacade.class);
        org.bukkit.Server bukkitServer = mock(org.bukkit.Server.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        Runnable task = mock(Runnable.class);

        when(server.server()).thenReturn(bukkitServer);
        when(bukkitServer.getConsoleSender()).thenReturn(console);

        OrzUtil.executeConsoleCmd(server, task, "say hello", "gamemode creative @p");

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.captor();
        verify(server).runSync(captor.capture());

        // Execute the captured runnable
        captor.getValue().run();

        verify(bukkitServer).dispatchCommand(console, "say hello");
        verify(bukkitServer).dispatchCommand(console, "gamemode creative @p");
        verify(task).run();
    }

    @Test
    void executeConsoleCmd_handlesNullTask() {
        ServerFacade server = mock(ServerFacade.class);
        org.bukkit.Server bukkitServer = mock(org.bukkit.Server.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        when(server.server()).thenReturn(bukkitServer);
        when(bukkitServer.getConsoleSender()).thenReturn(console);

        OrzUtil.executeConsoleCmd(server, null, "say hello");

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.captor();
        verify(server).runSync(captor.capture());

        captor.getValue().run();

        verify(bukkitServer).dispatchCommand(console, "say hello");
        verifyNoInteractions(console); // dispatchCommand is on bukkitServer, not console directly
    }

    @Test
    void executeConsoleCmd_handlesEmptyCommands() {
        ServerFacade server = mock(ServerFacade.class);
        org.bukkit.Server bukkitServer = mock(org.bukkit.Server.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        Runnable task = mock(Runnable.class);

        when(server.server()).thenReturn(bukkitServer);
        when(bukkitServer.getConsoleSender()).thenReturn(console);

        OrzUtil.executeConsoleCmd(server, task);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.captor();
        verify(server).runSync(captor.capture());

        captor.getValue().run();

        verify(bukkitServer, never()).dispatchCommand(any(), any());
        verify(task).run();
    }
}
