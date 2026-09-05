package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.TargetType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImMessageRouterTest {

    private static ImConversation conv(boolean enabled, String adminGroup, String playerGroup, String adminDm) {
        return new ImConversation(enabled, adminGroup, playerGroup, adminDm);
    }

    @Test
    void publicTargets_prefersPlayerGroup() {
        List<ImConversation> all = List.of(conv(true, "qq:admin", "qq:player", "qq:dm"));

        assertEquals(List.of("qq:player"), ImMessageRouter.publicTargets(all));
    }

    @Test
    void publicTargets_fallsBackToAdminGroupWhenPlayerEmpty() {
        List<ImConversation> all = List.of(conv(true, "qq:admin", "", "qq:dm"));

        assertEquals(List.of("qq:admin"), ImMessageRouter.publicTargets(all));
    }

    @Test
    void publicTargets_skipsDisabledAndEmptyTargets() {
        List<ImConversation> all = List.of(
                conv(false, "qq:disabled-admin", "qq:disabled-player", "qq:dm"),
                conv(true, "", "", "qq:dm"),
                conv(true, "qq:admin", null, "qq:dm"));

        assertEquals(List.of("qq:admin"), ImMessageRouter.publicTargets(all));
    }

    @Test
    void privateTargets_usesAdminDmOnly() {
        List<ImConversation> all =
                List.of(conv(true, "qq:admin", "qq:player", "qq:dm"), conv(false, "qq:admin2", "qq:player2", "qq:dm2"));

        assertEquals(List.of("qq:dm"), ImMessageRouter.privateTargets(all));
    }

    @Test
    void privateTargets_skipsEmptyAdminDm() {
        List<ImConversation> all = List.of(conv(true, "qq:admin", "", ""));

        assertTrue(ImMessageRouter.privateTargets(all).isEmpty());
    }

    @Test
    void resolveTargets_nullSafe() {
        assertTrue(ImMessageRouter.resolveTargets(null, List.of()).isEmpty());
        assertTrue(ImMessageRouter.resolveTargets(TargetType.PUBLIC, null).isEmpty());
    }

    @Test
    void resolveTargets_routesByTargetType() {
        List<ImConversation> all = List.of(conv(true, "qq:admin", "qq:player", "qq:dm"));

        assertEquals(List.of("qq:player"), ImMessageRouter.resolveTargets(TargetType.PUBLIC, all));
        assertEquals(List.of("qq:dm"), ImMessageRouter.resolveTargets(TargetType.PRIVATE, all));
    }

    @Test
    void iterationOrderPreserved() {
        List<ImConversation> all = List.of(
                conv(true, "a:admin", "a:player", ""),
                conv(true, "b:admin", "b:player", ""),
                conv(true, "c:admin", "", ""));

        assertEquals(List.of("a:player", "b:player", "c:admin"), ImMessageRouter.publicTargets(all));
    }

    @Test
    void isInboundAllowed_onlyWithinEnabledSessionTargets() {
        ImConversation all = conv(true, "qq:admin", "qq:player", "qq:dm");

        assertTrue(ImMessageRouter.isInboundAllowed(all, "qq:admin"));
        assertTrue(ImMessageRouter.isInboundAllowed(all, "qq:player"));
        assertTrue(ImMessageRouter.isInboundAllowed(all, "qq:dm"));
        assertFalse(ImMessageRouter.isInboundAllowed(all, "qq:unknown-chat"));
        assertFalse(ImMessageRouter.isInboundAllowed(all, null));
    }

    @Test
    void isInboundAllowed_disabledOrNullConversationRejects() {
        assertFalse(ImMessageRouter.isInboundAllowed(conv(false, "qq:admin", "", ""), "qq:admin"));
        assertFalse(ImMessageRouter.isInboundAllowed(null, "qq:admin"));
    }
}
