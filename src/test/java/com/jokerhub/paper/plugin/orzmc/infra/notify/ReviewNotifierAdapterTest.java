package com.jokerhub.paper.plugin.orzmc.infra.notify;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ReviewNotifierAdapter 群广播 fallback 渲染测试。
 *
 * <p>防回归：templates.yml 缺失 rank_promoted/rank_demoted 键时（默认配置/旧配置），
 * fallback switch 必须给出真实文案，不得落 default 的 "{message}" 占位符
 * （2026-08-07 实测线上群消息原样输出 {message} 的根因）。</p>
 */
class ReviewNotifierAdapterTest {

    private TypedConfigProvider configs;
    private Notifier notifier;
    private ReviewNotifierAdapter adapter;

    @BeforeEach
    void setUp() {
        configs = mock(TypedConfigProvider.class);
        notifier = mock(Notifier.class);
        adapter = new ReviewNotifierAdapter(configs, notifier);
        // 配置缺失 → renderTemplate 直接用 fallback
        when(configs.renderTemplate(anyString(), anyMap(), anyString()))
                .thenAnswer(inv -> new MessageEnvelope(
                        MessageEnvelope.TargetType.PUBLIC,
                        (String) inv.getArgument(2),
                        MessageEnvelope.Format.DEFAULT));
    }

    @Test
    void groupEvent_rankPromoted_rendersRealCopyNotPlaceholder() {
        adapter.groupEvent("rank_promoted", Map.of("player", "Alice", "group", "管理员"));

        verify(configs).renderTemplate(eq("rank_promoted"), anyMap(), contains("🎉"));
    }

    @Test
    void groupEvent_rankDemoted_rendersRealCopyNotPlaceholder() {
        adapter.groupEvent("rank_demoted", Map.of("player", "Alice", "group", "成员"));

        verify(configs).renderTemplate(eq("rank_demoted"), anyMap(), contains("⬇️"));
    }

    @Test
    void groupEvent_reviewSubmitted_rendersNewBlockStyle() {
        adapter.groupEvent(
                "review_submitted", Map.of("player", "StyleApp", "type", "晋升建造者", "summary", "申请晋升建造者：想用WorldEdit"));

        // fallback 是模板字面（{player} 占位符），断言新样式外壳而非已替换的玩家名
        verify(configs).renderTemplate(eq("review_submitted"), anyMap(), contains("🙋🏻‍♂️ [申请发起] {player}"));
    }

    @Test
    void groupEvent_reviewCancelled_rendersNewBlockStyle() {
        adapter.groupEvent(
                "review_cancelled", Map.of("player", "StyleApp", "type", "晋升建造者", "summary", "申请晋升 builder：样式测试申请-撤回"));

        verify(configs).renderTemplate(eq("review_cancelled"), anyMap(), contains("↩️ [申请撤回] {player}"));
    }

    @Test
    void groupEvent_reviewApproved_rendersNewBlockStyleWithReviewer() {
        adapter.groupEvent(
                "review_approved",
                Map.of("player", "StyleApp", "type", "晋升建造者", "summary", "申请晋升建造者：异步修复验证", "reviewer", "StyleAdm"));

        verify(configs).renderTemplate(eq("review_approved"), anyMap(), contains("✅ [申请通过] {player}"));
        verify(configs).renderTemplate(eq("review_approved"), anyMap(), contains("审核人：{reviewer}"));
    }

    @Test
    void groupEvent_reviewRejected_rendersNewBlockStyleWithReviewer() {
        adapter.groupEvent(
                "review_rejected",
                Map.of("player", "StyleApp", "type", "晋升建造者", "summary", "申请晋升建造者：样式测试申请-通过", "reviewer", "StyleAdm"));

        verify(configs).renderTemplate(eq("review_rejected"), anyMap(), contains("❌ [申请拒绝] {player}"));
        verify(configs).renderTemplate(eq("review_rejected"), anyMap(), contains("审核人：{reviewer}"));
    }

    @Test
    void groupEvent_unknownKey_fallsBackToPlaceholder() {
        // 未登记的键仍走 default "{message}"（既有行为，不回归）
        adapter.groupEvent("unknown_event", Map.of());

        verify(configs).renderTemplate(eq("unknown_event"), anyMap(), eq("{message}"));
    }
}
