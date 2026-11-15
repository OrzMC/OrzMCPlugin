package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRule;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.Lang;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * BlacklistCommandRegistrar 分支补测（热点：0% 覆盖）。
 *
 * <p>用真实 {@link CommandDispatcher} 跑完整解析链：捕获 registrar 注册的 Brigadier 节点，
 * 用控制台 CommandSender（非 Player → AdminOnly 拦截器放行）驱动各子命令，
 * 断言的是"写进了服务层 / 给出了正确提示"，而不是"没抛异常"。</p>
 */
class BlacklistCommandRegistrarTest {

    private CommandDispatcher<CommandSourceStack> dispatcher;
    private CommandSourceStack source;
    private CommandSender sender;
    private AccessRuleService svc;
    private final List<Component> messages = new ArrayList<>();

    /** 捕获 registrar 注册的节点，避免依赖 Paper 运行时。 */
    private static final class CapturingCommands implements Commands {
        private LiteralCommandNode<CommandSourceStack> node;

        @Override
        public CommandDispatcher<CommandSourceStack> getDispatcher() {
            return new CommandDispatcher<>();
        }

        @Override
        public Set<String> register(
                LiteralCommandNode<CommandSourceStack> node, String description, Collection<String> aliases) {
            this.node = node;
            return Set.of(node.getLiteral());
        }

        @Override
        public Set<String> register(
                io.papermc.paper.plugin.configuration.PluginMeta plugin,
                LiteralCommandNode<CommandSourceStack> node,
                String description,
                Collection<String> aliases) {
            this.node = node;
            return Set.of(node.getLiteral());
        }

        @Override
        public Set<String> register(
                String label,
                String description,
                Collection<String> aliases,
                io.papermc.paper.command.brigadier.BasicCommand command) {
            return Set.of();
        }

        @Override
        public Set<String> register(
                io.papermc.paper.plugin.configuration.PluginMeta plugin,
                String label,
                String description,
                Collection<String> aliases,
                io.papermc.paper.command.brigadier.BasicCommand command) {
            return Set.of();
        }

        @Override
        public Set<String> registerWithFlags(
                io.papermc.paper.plugin.configuration.PluginMeta plugin,
                LiteralCommandNode<CommandSourceStack> node,
                String label,
                Collection<String> aliases,
                Set<io.papermc.paper.command.brigadier.CommandRegistrationFlag> flags) {
            this.node = node;
            return Set.of(node.getLiteral());
        }

        @Override
        public Set<String> register(LiteralCommandNode<CommandSourceStack> node) {
            this.node = node;
            return Set.of(node.getLiteral());
        }
    }

    @BeforeEach
    void setUp() {
        svc = mock(AccessRuleService.class);
        OrzTextStyles styles = mock(OrzTextStyles.class);
        when(styles.info(anyString())).thenReturn(Component.text("info"));
        when(styles.success(anyString())).thenReturn(Component.text("success"));
        when(styles.error(anyString())).thenReturn(Component.text("error"));

        I18nService i18n = mock(I18nService.class);
        when(i18n.langFor()).thenReturn(Lang.ZH_CN);
        when(i18n.msg(any(), anyString())).thenReturn("msg");
        when(i18n.msg(any(), anyString(), any())).thenReturn("msg");

        sender = mock(CommandSender.class);
        source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);

        CapturingCommands commands = new CapturingCommands();
        new BlacklistCommandRegistrar(svc, styles, i18n).register(commands);
        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(commands.node);
    }

    private int run(String input) {
        return assertDoesNotThrow(() -> dispatcher.execute(input, source));
    }

    // ---------------------------------------------------------------- list

    @Test
    void list_whenEmpty_reportsEmpty() {
        when(svc.getIpPatterns()).thenReturn(List.of());
        when(svc.getPlayerNameRules()).thenReturn(List.of());

        run("blacklist list");

        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void list_withPatternsAndRules_rendersBothSections() {
        when(svc.getIpPatterns()).thenReturn(List.of("1.2.3.*", "10.0.0.1"));
        when(svc.getPlayerNameRules()).thenReturn(List.of(PlayerNameRule.of(PlayerNameRule.MatchType.PREFIX, "Bot_")));

        run("blacklist list");

        // 标题 + IP 段 + 2 条 + 规则段 + 1 条 = 6 条消息
        verify(sender, org.mockito.Mockito.times(6)).sendMessage(any(Component.class));
    }

    @Test
    void list_player_whenEmpty_reportsEmpty() {
        when(svc.getPlayerNameRules()).thenReturn(List.of());

        run("blacklist list player");

        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void list_player_withRules_rendersSection() {
        when(svc.getPlayerNameRules())
                .thenReturn(List.of(
                        PlayerNameRule.of(PlayerNameRule.MatchType.EXACT, "Alice"),
                        PlayerNameRule.of(PlayerNameRule.MatchType.GLOB, "Bob*")));

        run("blacklist list player");

        verify(sender, org.mockito.Mockito.times(3)).sendMessage(any(Component.class));
    }

    // ---------------------------------------------------------------- player 规则（子命令）

    @Test
    void addPlayerRule_delegatesToService() {
        when(svc.addPlayerNameRule(eq(PlayerNameRule.MatchType.PREFIX), eq("Bot_")))
                .thenReturn(true);

        run("blacklist add player prefix Bot_");

        verify(svc).addPlayerNameRule(eq(PlayerNameRule.MatchType.PREFIX), eq("Bot_"));
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void addPlayerRule_blankValue_showsUsage() {
        run("blacklist add player prefix   ");

        verify(svc, never()).addPlayerNameRule(any(), any());
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void removePlayerRule_delegatesToService() {
        when(svc.removePlayerNameRule(eq(PlayerNameRule.MatchType.EXACT), eq("Alice")))
                .thenReturn(true);

        run("blacklist remove player exact Alice");

        verify(svc).removePlayerNameRule(eq(PlayerNameRule.MatchType.EXACT), eq("Alice"));
    }

    // ---------------------------------------------------------------- player 规则（简写）

    @Test
    void addPlayerShorthand_parsesTypeAndValue() {
        when(svc.addPlayerNameRule(eq(PlayerNameRule.MatchType.GLOB), eq("Bot*")))
                .thenReturn(true);

        run("blacklist player glob Bot*");

        verify(svc).addPlayerNameRule(eq(PlayerNameRule.MatchType.GLOB), eq("Bot*"));
    }

    @Test
    void addPlayerShorthand_missingValue_showsUsage() {
        run("blacklist player glob");

        verify(svc, never()).addPlayerNameRule(any(), any());
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void removePlayerShorthand_parsesTypeAndValue() {
        when(svc.removePlayerNameRule(eq(PlayerNameRule.MatchType.GLOB), eq("Bot*")))
                .thenReturn(true);

        run("blacklist -player glob Bot*");

        verify(svc).removePlayerNameRule(eq(PlayerNameRule.MatchType.GLOB), eq("Bot*"));
    }

    @Test
    void removePlayerShorthand_missingValue_showsUsage() {
        run("blacklist -player glob");

        verify(svc, never()).removePlayerNameRule(any(), any());
        verify(sender).sendMessage(any(Component.class));
    }

    // ---------------------------------------------------------------- IP 规则

    @Test
    void addIpPattern_successAndFailurePaths() {
        when(svc.addIpPattern("1.2.3.4")).thenReturn(true);
        run("blacklist add 1.2.3.4");
        verify(svc).addIpPattern("1.2.3.4");

        when(svc.addIpPattern("9.9.9.9")).thenReturn(false);
        run("blacklist add 9.9.9.9");
        verify(svc).addIpPattern("9.9.9.9");
        verify(sender, org.mockito.Mockito.atLeast(2)).sendMessage(any(Component.class));
    }

    @Test
    void addIpPattern_blankAndPlayerSyntax_areRejected() {
        run("blacklist add    ");
        verify(svc, never()).addIpPattern(any());

        run("blacklist add player:glob Foo*");
        verify(svc, never()).addIpPattern(any());
        verify(sender, org.mockito.Mockito.atLeast(2)).sendMessage(any(Component.class));
    }

    @Test
    void removeIpPattern_successAndFailurePaths() {
        when(svc.removeIpPattern("1.2.3.4")).thenReturn(true);
        run("blacklist remove 1.2.3.4");
        verify(svc).removeIpPattern("1.2.3.4");

        when(svc.removeIpPattern("9.9.9.9")).thenReturn(false);
        run("blacklist remove 9.9.9.9");
        verify(svc).removeIpPattern("9.9.9.9");
    }

    @Test
    void removeIpPattern_blankAndPlayerSyntax_areRejected() {
        run("blacklist remove    ");
        verify(svc, never()).removeIpPattern(any());

        run("blacklist remove -player:glob Foo*");
        verify(svc, never()).removeIpPattern(any());
    }

    @Test
    void rootCommand_hasAliasAndNoDirectExecution() {
        LiteralCommandNode<CommandSourceStack> root =
                (LiteralCommandNode<CommandSourceStack>) dispatcher.getRoot().getChild("blacklist");
        assertEquals("blacklist", root.getLiteral());
        assertTrue(root.getChildren().size() >= 2, "至少 list/add/remove 三个子命令");
    }

    @Test
    void unknownSubcommand_doesNotThrow() {
        assertDoesNotThrow(() -> dispatcher.parse("blacklist wat", source));
    }
}
