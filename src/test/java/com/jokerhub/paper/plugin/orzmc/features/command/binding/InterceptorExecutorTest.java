package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import static org.mockito.Mockito.*;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterceptorExecutorTest {

    @Mock
    private CommandSender sender;

    @Mock
    private Command command;

    @Mock
    private CommandExecutor delegate;

    @Test
    void onCommand_noInterceptors_delegates() {
        InterceptorExecutor exec = new InterceptorExecutor("test", delegate, List.of());
        exec.onCommand(sender, command, "test", new String[0]);
        verify(delegate).onCommand(sender, command, "test", new String[0]);
    }

    @Test
    void onCommand_interceptorBlocks_doesNotDelegate() {
        CommandInterceptor block = mock(CommandInterceptor.class);
        when(block.preHandle(sender, "test")).thenReturn(Component.text("blocked"));

        InterceptorExecutor exec = new InterceptorExecutor("test", delegate, List.of(block));
        exec.onCommand(sender, command, "test", new String[0]);

        verify(sender).sendMessage(Component.text("blocked"));
        verifyNoInteractions(delegate);
    }

    @Test
    void onCommand_interceptorPasses_delegates() {
        CommandInterceptor pass = mock(CommandInterceptor.class);
        when(pass.preHandle(sender, "test")).thenReturn(null);

        InterceptorExecutor exec = new InterceptorExecutor("test", delegate, List.of(pass));
        exec.onCommand(sender, command, "test", new String[0]);

        verify(delegate).onCommand(sender, command, "test", new String[0]);
    }
}
