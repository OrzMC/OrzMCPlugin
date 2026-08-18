package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.SecurityGuardConfig;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CommandGuardEventServiceTest {

    private TypedConfigProvider configs;
    private Notifier notifier;
    private CommandAuditService audit;
    private Logger logger;
    private CommandGuardEventService service;

    @BeforeEach
    void setUp() {
        configs = mock(TypedConfigProvider.class);
        notifier = mock(Notifier.class);
        audit = mock(CommandAuditService.class);
        logger = mock(Logger.class);
    }

    private void withConfig(SecurityGuardConfig config) {
        when(configs.securityGuard()).thenReturn(config);
        when(configs.renderTemplate(eq(TemplateKeys.COMMAND_GUARD_BLOCKED), anyMap(), anyString()))
                .thenReturn(MessageEnvelope.publicMessage("envelope"));
        service = new CommandGuardEventService(
                new CommandGuardService(() -> configs.securityGuard()),
                configs,
                notifier,
                new CommandFeedbackService(),
                audit,
                logger);
    }

    private static PlayerCommandPreprocessEvent playerEvent(String command, Player player) {
        PlayerCommandPreprocessEvent event = mock(PlayerCommandPreprocessEvent.class);
        when(event.getMessage()).thenReturn(command);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private static ServerCommandEvent serverEvent(String command, CommandSender sender) {
        ServerCommandEvent event = mock(ServerCommandEvent.class);
        when(event.getCommand()).thenReturn(command);
        when(event.getSender()).thenReturn(sender);
        return event;
    }

    // ---- BLOCK：玩家聊天栏命令 ----

    @Test
    void playerBlocked_cancelsSendsFeedbackAndNotifiesAdmins() {
        withConfig(new SecurityGuardConfig(true, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, true, true));
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("steve");
        PlayerCommandPreprocessEvent event = playerEvent("/op steve", player);

        service.onPlayerCommand(event);

        verify(event).setCancelled(true);
        ArgumentCaptor<Component> feedbackCaptor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(feedbackCaptor.capture());
        assertInstanceOf(TextComponent.class, feedbackCaptor.getValue());
        assertTrue(((TextComponent) feedbackCaptor.getValue()).content().contains("已被安全拦截"));
        verify(audit).record(CommandAuditService.SOURCE_GAME, "steve", "/op steve", true);
        verify(configs).renderTemplate(eq(TemplateKeys.COMMAND_GUARD_BLOCKED), anyMap(), contains("op steve"));
        verify(notifier).event(eq(TemplateKeys.COMMAND_GUARD_BLOCKED), any(MessageEnvelope.class));
        verify(logger, never()).warning(anyString());
    }

    @Test
    void playerBlocked_notifyAdminsDisabled_noNotifierCall() {
        withConfig(new SecurityGuardConfig(true, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, false, true));
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("steve");
        PlayerCommandPreprocessEvent event = playerEvent("/op steve", player);

        service.onPlayerCommand(event);

        verify(event).setCancelled(true);
        verify(player).sendMessage(any(Component.class));
        verify(audit).record(CommandAuditService.SOURCE_GAME, "steve", "/op steve", true);
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
    }

    @Test
    void playerBlocked_withVars() {
        withConfig(new SecurityGuardConfig(true, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, true, true));
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("steve");
        PlayerCommandPreprocessEvent event = playerEvent("/op steve", player);

        service.onPlayerCommand(event);

        // 变量应包含 command / source / sender / reason
        verify(configs)
                .renderTemplate(
                        eq(TemplateKeys.COMMAND_GUARD_BLOCKED),
                        argThat(vars -> "/op steve".equals(vars.get("command"))
                                && "玩家".equals(vars.get("source"))
                                && "steve".equals(vars.get("sender"))
                                && vars.get("reason").contains("deny-list")),
                        anyString());
    }

    // ---- BLOCK：控制台命令 ----

    @Test
    void serverCommandBlocked_cancelsAndNotifies() {
        withConfig(new SecurityGuardConfig(true, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, true, true));
        CommandSender console = mock(CommandSender.class);
        when(console.getName()).thenReturn("CONSOLE");
        ServerCommandEvent event = serverEvent("stop", console);

        service.onServerCommand(event);

        verify(event).setCancelled(true);
        verify(console).sendMessage(any(Component.class));
        verify(audit).record(CommandAuditService.SOURCE_CONSOLE, "CONSOLE", "stop", true);
        verify(configs)
                .renderTemplate(
                        eq(TemplateKeys.COMMAND_GUARD_BLOCKED),
                        argThat(vars -> "控制台/RCON".equals(vars.get("source")) && "CONSOLE".equals(vars.get("sender"))),
                        anyString());
        verify(notifier).event(eq(TemplateKeys.COMMAND_GUARD_BLOCKED), any(MessageEnvelope.class));
    }

    // ---- WARN：放行 + 日志 ----

    @Test
    void warn_allowsButLogsWarning() {
        withConfig(new SecurityGuardConfig(true, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, true, true));
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("steve");
        PlayerCommandPreprocessEvent event = playerEvent("/kill @e", player);

        service.onPlayerCommand(event);

        verify(event, never()).setCancelled(anyBoolean());
        verify(player, never()).sendMessage(any(Component.class));
        verify(audit).record(CommandAuditService.SOURCE_GAME, "steve", "/kill @e", false);
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
        verify(logger).warning(contains("kill @e"));
    }

    // ---- ALLOW：无副作用 ----

    @Test
    void allow_noSideEffects() {
        withConfig(new SecurityGuardConfig(true, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, true, true));
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("steve");
        PlayerCommandPreprocessEvent event = playerEvent("/say hello", player);

        service.onPlayerCommand(event);

        verify(event, never()).setCancelled(anyBoolean());
        verify(player, never()).sendMessage(any(Component.class));
        verify(audit).record(CommandAuditService.SOURCE_GAME, "steve", "/say hello", false);
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
        verify(logger, never()).warning(anyString());
    }

    // ---- guard 关闭：全部放行（审计仍记录 executed）----

    @Test
    void guardDisabled_allowsEverything() {
        withConfig(new SecurityGuardConfig(false, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, true, true));
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("steve");
        PlayerCommandPreprocessEvent event = playerEvent("/op steve", player);

        service.onPlayerCommand(event);

        verify(event, never()).setCancelled(anyBoolean());
        verify(player, never()).sendMessage(any(Component.class));
        verify(audit).record(CommandAuditService.SOURCE_GAME, "steve", "/op steve", false);
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
        verify(logger, never()).warning(anyString());
    }
}
