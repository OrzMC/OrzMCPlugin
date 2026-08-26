package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRuleFeedback.Outcome;
import org.junit.jupiter.api.Test;

/** 玩家名规则增删反馈构建（bot $d 与游戏 /blacklist 共用）——补齐游戏侧反馈逻辑的测试覆盖。 */
class PlayerNameRuleFeedbackTest {

    private final AccessRuleService svc = mock(AccessRuleService.class);

    @Test
    void add_success_returnsAddedMessage() {
        when(svc.addPlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo")).thenReturn(true);

        Outcome o = PlayerNameRuleFeedback.feedback(svc, "exact", "foo", false);

        assertTrue(o.success());
        assertEquals("已添加玩家名规则: exact:foo", o.message());
        verify(svc).addPlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo");
        verify(svc, never()).removePlayerNameRule(any(), any());
    }

    @Test
    void add_duplicate_returnsAlreadyExists() {
        when(svc.addPlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo")).thenReturn(false);

        Outcome o = PlayerNameRuleFeedback.feedback(svc, "exact", "foo", false);

        assertTrue(o.success());
        assertEquals("玩家名规则已存在，未重复添加: exact:foo", o.message());
        verify(svc).addPlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo");
    }

    @Test
    void add_blankValue_returnsEmptyValueError_andDoesNotTouchService() {
        Outcome o = PlayerNameRuleFeedback.feedback(svc, "exact", "   ", false);

        assertFalse(o.success());
        assertEquals("规则值不能为空", o.message());
        verifyNoInteractions(svc);
    }

    @Test
    void add_trimsTrailingSpace_beforeAddAndDisplay() {
        when(svc.addPlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo")).thenReturn(true);

        Outcome o = PlayerNameRuleFeedback.feedback(svc, "exact", "foo  ", false);

        assertTrue(o.success());
        assertEquals("已添加玩家名规则: exact:foo", o.message());
        verify(svc).addPlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo");
    }

    @Test
    void remove_present_returnsRemovedMessage() {
        when(svc.removePlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo")).thenReturn(true);

        Outcome o = PlayerNameRuleFeedback.feedback(svc, "exact", "foo", true);

        assertTrue(o.success());
        assertEquals("已移除玩家名规则: exact:foo", o.message());
    }

    @Test
    void remove_missing_returnsNotFound() {
        when(svc.removePlayerNameRule(PlayerNameRule.MatchType.EXACT, "foo")).thenReturn(false);

        Outcome o = PlayerNameRuleFeedback.feedback(svc, "exact", "foo", true);

        assertFalse(o.success());
        assertEquals("未找到该玩家名规则: exact:foo", o.message());
    }

    @Test
    void invalidType_returnsInvalidTypeFailure_andDoesNotTouchService() {
        Outcome o = PlayerNameRuleFeedback.feedback(svc, "bogus", "foo", false);

        assertFalse(o.success());
        assertEquals("无效匹配类型: bogus（支持 exact/prefix/suffix/contains/glob/regex）", o.message());
        verifyNoInteractions(svc);
    }

    @Test
    void invalidRegex_returnsInvalidRegexFailure() {
        Outcome o = PlayerNameRuleFeedback.feedback(svc, "regex", "[", false);

        assertFalse(o.success());
        assertEquals("无效的正则表达式: [", o.message());
        verifyNoInteractions(svc);
    }

    @Test
    void typeIsCaseInsensitive() {
        when(svc.removePlayerNameRule(PlayerNameRule.MatchType.PREFIX, "bot_")).thenReturn(true);

        Outcome o = PlayerNameRuleFeedback.feedback(svc, "PREFIX", "bot_", true);

        assertTrue(o.success());
        assertEquals("已移除玩家名规则: prefix:bot_", o.message());
    }
}
