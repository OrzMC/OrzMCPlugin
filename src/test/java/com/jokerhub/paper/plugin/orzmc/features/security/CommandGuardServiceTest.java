package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.features.security.CommandGuardService.GuardDecision;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.SecurityGuardConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandGuardServiceTest {

    private static CommandGuardService service(SecurityGuardConfig config) {
        return new CommandGuardService(() -> config);
    }

    private static CommandGuardService defaultService() {
        return service(new SecurityGuardConfig(true, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, true, true));
    }

    // ---- 基础放行 ----

    @Test
    void nullOrBlank_alwaysAllowed() {
        CommandGuardService svc = defaultService();
        assertEquals(
                CommandGuardService.GuardDecision.Decision.ALLOW,
                svc.guard(null).kind());
        assertEquals(
                CommandGuardService.GuardDecision.Decision.ALLOW,
                svc.guard("  ").kind());
    }

    @Test
    void disabledConfig_allowsEvenBlockedCommands() {
        CommandGuardService svc =
                service(new SecurityGuardConfig(false, SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, true, true));
        assertEquals(
                CommandGuardService.GuardDecision.Decision.ALLOW,
                svc.guard("op steve").kind());
    }

    @Test
    void normalCommand_allowed() {
        assertEquals(
                CommandGuardService.GuardDecision.Decision.ALLOW,
                defaultService().guard("say hello").kind());
        assertEquals(
                CommandGuardService.GuardDecision.Decision.ALLOW,
                defaultService().guard("/tp player 0 64 0").kind());
    }

    // ---- deny-list ----

    @Test
    void denyListExactMatch_blocked() {
        assertEquals(
                GuardDecision.Decision.BLOCK, defaultService().guard("op steve").kind());
        assertEquals(
                GuardDecision.Decision.BLOCK, defaultService().guard("/reload").kind());
        assertEquals(
                GuardDecision.Decision.BLOCK, defaultService().guard("stop").kind());
        assertEquals(
                GuardDecision.Decision.BLOCK, defaultService().guard("publish").kind());
        assertEquals(
                GuardDecision.Decision.BLOCK, defaultService().guard("seed").kind());
    }

    @Test
    void denyList_normalizesCaseAndPrefix() {
        assertEquals(
                GuardDecision.Decision.BLOCK,
                defaultService().guard("/OP Steve").kind());
        assertEquals(
                GuardDecision.Decision.BLOCK,
                defaultService().guard("minecraft:op Steve").kind());
        assertEquals(
                GuardDecision.Decision.BLOCK,
                defaultService().guard("/minecraft:reload").kind());
    }

    @Test
    void denyList_similarCommandNamesNotBlocked() {
        assertEquals(
                GuardDecision.Decision.ALLOW,
                defaultService().guard("opsay hello").kind());
        assertEquals(
                GuardDecision.Decision.ALLOW,
                defaultService().guard("stopserver").kind());
    }

    @Test
    void multiWordRule_matchesCommandPrefix() {
        // 仅把 "plugman reload" 列为 deny 项：reload 子命令拦截，其它 plugman 子命令放行
        CommandGuardService svc = service(new SecurityGuardConfig(true, List.of("plugman reload"), true, true));
        assertEquals(
                GuardDecision.Decision.BLOCK,
                svc.guard("plugman reload example").kind());
        assertEquals(GuardDecision.Decision.BLOCK, svc.guard("plugman reload").kind());
        assertEquals(
                GuardDecision.Decision.ALLOW,
                svc.guard("plugman enable example").kind());
    }

    @Test
    void emptyDenyList_allowsEverything() {
        CommandGuardService svc = service(new SecurityGuardConfig(true, List.of(), true, true));
        assertEquals(GuardDecision.Decision.ALLOW, svc.guard("op steve").kind());
    }

    // ---- 目标选择器守护 ----

    @Test
    void bareSelectorInSensitiveCommand_warns() {
        assertEquals(
                GuardDecision.Decision.WARN, defaultService().guard("kill @e").kind());
        assertEquals(
                GuardDecision.Decision.WARN, defaultService().guard("clear @a").kind());
        assertEquals(
                GuardDecision.Decision.WARN,
                defaultService().guard("give @a diamond 64").kind());
        assertEquals(
                GuardDecision.Decision.WARN,
                defaultService().guard("kill @e[type=!player]").kind());
        assertEquals(
                GuardDecision.Decision.WARN,
                defaultService().guard("execute as @a run say hi").kind());
        // 有方括号但没限定 type/distance（如仅 tag）仍视为危险
        assertEquals(
                GuardDecision.Decision.WARN,
                defaultService().guard("kill @e[tag=marked]").kind());
    }

    @Test
    void qualifiedSelector_inSensitiveCommand_allowed() {
        assertEquals(
                GuardDecision.Decision.ALLOW,
                defaultService()
                        .guard("kill @e[type=minecraft:zombie,distance=..32]")
                        .kind());
        assertEquals(
                GuardDecision.Decision.ALLOW,
                defaultService().guard("kill @e[distance=..10]").kind());
        assertEquals(
                GuardDecision.Decision.ALLOW,
                defaultService().guard("clear @a[type=player]").kind());
    }

    @Test
    void selectorInNonSensitiveCommand_allowed() {
        assertEquals(
                GuardDecision.Decision.ALLOW, defaultService().guard("say @a").kind());
        assertEquals(
                GuardDecision.Decision.ALLOW, defaultService().guard("me @e").kind());
        assertEquals(
                GuardDecision.Decision.ALLOW,
                defaultService().guard("tp @e 0 64 0").kind());
    }

    @Test
    void warnDecision_isNotBlock() {
        GuardDecision d = defaultService().guard("kill @e");
        assertTrue(d.warned());
        assertFalse(d.blocked());
    }
}
