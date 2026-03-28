package com.jokerhub.paper.plugin.orzmc.commands;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.features.bot.BotStatusService;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OrzBotStatusTest {
    @Mock
    private BotStatusService statusService;

    @Mock
    private BotMessageService botMessageService;

    @Mock
    private CommandSender sender;

    @Mock
    private Command command;

    @Test
    void triggersReconnectBeforeSendingStatus() {
        Component status = Component.text("ok");
        when(statusService.buildStatusMessage()).thenReturn(status);

        OrzBotStatus executor = new OrzBotStatus(statusService, botMessageService);
        executor.onCommand(sender, command, "bot", new String[0]);

        InOrder ordered = inOrder(botMessageService, statusService, sender);
        ordered.verify(botMessageService).tryReconnectQqWsIfDisconnected();
        ordered.verify(statusService).buildStatusMessage();
        ordered.verify(sender).sendMessage(status);
    }
}
