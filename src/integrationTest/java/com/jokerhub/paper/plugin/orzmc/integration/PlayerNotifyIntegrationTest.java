package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * 玩家上下线通知聚合的 MockBukkit 集成测试。
 *
 * <p>在真实 MockBukkit 服务器上装载完整插件，用真实 Bukkit 事件
 * （{@code addPlayer} → PlayerJoinEvent、{@code disconnect} → PlayerQuitEvent、
 * {@code kick} → PlayerKickEvent）驱动整条链路：
 * 事件监听器 → PlayerEventService → PlayerEventAggregator → Notifier → CapturingSink。
 *
 * <p>验证聚合约束：窗口内多条事件合并为一条摘要且计数精确、单条事件延迟一个窗口仍走原模板、
 * 跨窗口无丢消息（事件总数 = Σ单条消息数 + Σ摘要计数）、名称截断不影响计数。
 */
@Tag("integration")
public class PlayerNotifyIntegrationTest {

    /** 聚合窗口（config.yml {@code player_notify.window_ms=3000ms} / 50 = 60 ticks），多 1 tick 确保冲刷执行。 */
    private static final long FLUSH_TICKS = 61;

    private ServerMock server;
    private OrzMC plugin;
    private CapturingSink sink;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OrzMC.class);
        // 关闭服务端白名单（enableForceWhitelist 已在 load 时打开它）：
        // MockBukkit 会在 PlayerJoinEvent 后把非白名单玩家移出在线列表，
        // 导致冲刷时刻 online_count 恒为 0。与真实服 E2E 的 force_whitelist=false 对齐。
        server.setWhitelist(false);
        sink = new CapturingSink();
        plugin.services().botModule().notifier().registerSink(sink);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    /** 推进调度器越过一个聚合窗口，触发窗口尾部冲刷。 */
    private void flushWindow() {
        server.getScheduler().performTicks(FLUSH_TICKS);
    }

    /** 上线：addPlayer 触发 PlayerJoinEvent（监听器入队聚合器）。 */
    private PlayerMock join(String name) {
        return server.addPlayer(name);
    }

    /** 下线：disconnect 触发 PlayerQuitEvent 并移出在线列表（与真实服语义一致）。 */
    private void quit(PlayerMock player) {
        player.disconnect();
    }

    /** 被踢：kick 触发 PlayerKickEvent 并移出在线列表。 */
    private void kick(PlayerMock player) {
        player.kick(Component.text("E2E kick"));
    }

    // ---- 单发：窗口内仅 1 条事件，延迟一个窗口走原单条模板 ----

    @Test
    public void singleJoin_rendersPlayerJoinTemplateAfterWindow() {
        join("e2e_0");
        Assertions.assertTrue(sink.isEmpty(), "聚合窗口内不应立即发送");
        flushWindow();
        Assertions.assertEquals(List.of("player_join"), sink.keys, "单发应复用 player_join 模板");
        String msg = sink.lastEnvelope().message();
        Assertions.assertTrue(msg.contains("e2e_0"), "消息应含玩家名: " + msg);
        Assertions.assertTrue(msg.contains("🥰 上线"), "应为单条上线模板: " + msg);
        Assertions.assertTrue(msg.contains("当前玩家(1/"), "冲刷时刻在线数为 1: " + msg);
    }

    @Test
    public void singleQuit_afterPreviousWindow_rendersPlayerQuitWithoutDoubleSubtract() {
        PlayerMock p = join("e2e_0");
        flushWindow(); // 窗口1：上线已发
        sink.clear();
        quit(p); // 触发 PlayerQuitEvent + 移出在线列表
        flushWindow(); // 窗口2
        Assertions.assertEquals(List.of("player_quit"), sink.keys);
        String msg = sink.lastEnvelope().message();
        Assertions.assertTrue(msg.contains("😋 下线"), "应为单条下线模板: " + msg);
        Assertions.assertTrue(msg.contains("当前玩家(0/"), "冲刷时刻当事人已离线，不应重复减 1: " + msg);
    }

    // ---- 多发：窗口内多条事件，合并为一条摘要，计数精确 ----

    @Test
    public void burstJoins_withinWindow_renderSingleDigestExactCount() {
        for (int i = 0; i < 6; i++) {
            join("e2e_" + i);
        }
        flushWindow();
        Assertions.assertEquals(List.of("player_digest"), sink.keys, "窗口内 6 条事件应合并为 1 条摘要");
        String msg = sink.lastEnvelope().message();
        Assertions.assertTrue(msg.contains("🥰 上线(6)："), "摘要计数应精确: " + msg);
        Assertions.assertTrue(msg.contains("e2e_0") && msg.contains("e2e_5"), "应列出玩家名: " + msg);
        Assertions.assertTrue(msg.contains("当前玩家(6/"), "冲刷时刻 6 人在线: " + msg);
    }

    @Test
    public void burstMixed_joinQuitKick_renderAllSummaries() {
        PlayerMock p0 = join("e2e_0");
        PlayerMock p1 = join("e2e_1");
        PlayerMock p2 = join("e2e_2");
        quit(p1);
        kick(p2);
        flushWindow();
        String msg = sink.lastEnvelope().message();
        Assertions.assertTrue(msg.contains("🥰 上线(3)："), "3 人都在窗口内上线，摘要应 (3): " + msg);
        Assertions.assertTrue(msg.contains("😋 下线："), "单人版块不显示人数, got: " + msg);
        Assertions.assertTrue(msg.contains("😂 被踢："), "单人版块不显示人数, got: " + msg);
        Assertions.assertTrue(msg.contains("当前玩家(1/"), "3 加入 -1 退出 -1 被踢 = 剩 1: " + msg);
    }

    @Test
    public void manyJoins_inOneWindow_truncateNamesButCountExact() {
        // max_list_items 默认 6：10 条事件 → 摘要计数 +10，名称仅显示前 6 并附 "等4人"
        for (int i = 0; i < 10; i++) {
            join("e2e_" + i);
        }
        flushWindow();
        String msg = sink.lastEnvelope().message();
        Assertions.assertTrue(msg.contains("🥰 上线(10)："), "计数应精确 (10): " + msg);
        Assertions.assertTrue(msg.contains("等4人"), "超出 max_list_items 应显示 等4人: " + msg);
    }

    // ---- 窗口边界与跨窗口无丢消息 ----

    @Test
    public void windowBoundary_singleEventsInSeparateWindows_keepSingleTemplates() {
        PlayerMock p = join("e2e_0");
        flushWindow(); // 窗口1：player_join
        quit(p);
        flushWindow(); // 窗口2：player_quit
        Assertions.assertEquals(List.of("player_join", "player_quit"), sink.keys, "各窗口独立，单发仍走单模板");
    }

    @Test
    public void manyEvents_acrossWindows_noMessageLoss() {
        // 窗口1：5 加入；窗口2：3 退出；窗口3：2 加入
        PlayerMock p1 = join("e2e_1");
        PlayerMock p2 = join("e2e_2");
        PlayerMock p3 = join("e2e_3");
        join("e2e_4");
        join("e2e_5");
        flushWindow();
        quit(p1);
        quit(p2);
        quit(p3);
        flushWindow();
        join("e2e_8");
        join("e2e_9");
        flushWindow();

        Totals totals = Totals.from(sink);
        Assertions.assertEquals(7, totals.join, "7 次加入全部被计数，无丢弃");
        Assertions.assertEquals(3, totals.quit, "3 次退出全部被计数，无丢弃");
        Assertions.assertEquals(0, totals.kick);
        Assertions.assertEquals(3, sink.keys.size(), "3 个窗口 → 恰好 3 条消息（每个窗口 1 条）");
    }

    /** 从捕获消息汇总各状态事件数（摘要按 +N/-N 计数，单条计 1）——与 tools/e2e 对账公式一致。 */
    private static final class Totals {
        private int join;
        private int quit;
        private int kick;

        static Totals from(CapturingSink sink) {
            Totals t = new Totals();
            for (int i = 0; i < sink.keys.size(); i++) {
                String key = sink.keys.get(i);
                String msg = sink.envelopes.get(i).message();
                switch (key) {
                    case "player_join" -> t.join++;
                    case "player_quit" -> t.quit++;
                    case "player_kick" -> t.kick++;
                    case "player_digest" -> {
                        t.join += count(msg, "🥰 上线(?:\\((\\d+)\\))?：");
                        t.quit += count(msg, "😋 下线(?:\\((\\d+)\\))?：");
                        t.kick += count(msg, "😂 被踢(?:\\((\\d+)\\))?：");
                    }
                    default -> {
                        // 未知事件键不参与对账
                    }
                }
            }
            return t;
        }

        private static int count(String text, String regex) {
            Matcher m = Pattern.compile(regex).matcher(text);
            int sum = 0;
            while (m.find()) {
                String g = m.group(1);
                // 版块头单人时不显示人数括号 → 计 1；多人时取括号内数字
                sum += (g == null || g.isEmpty()) ? 1 : Integer.parseInt(g);
            }
            return sum;
        }
    }
}
