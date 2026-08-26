package com.jokerhub.paper.plugin.orzmc.features.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.PlayerNotifyConfig;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class PlayerEventAggregatorTest extends ServiceTestBase {

    @Mock
    private ServerFacade server;

    @Mock
    private TypedConfigProvider configs;

    @Mock
    private Notifier notifier;

    @Mock
    private Server bukkitServer;

    @Mock
    private Logger logger;

    private OnlineListFormatter formatter;
    private PlayerEventAggregator aggregator;

    /** 调度器捕获队列：doAnswer 拦截 runLater，模拟真实调度（含失败重试的重调度）。 */
    private final ArrayDeque<Runnable> scheduledTasks = new ArrayDeque<>();

    @BeforeEach
    void setUp() {
        formatter = new OnlineListFormatter();
        when(configs.playerNotify()).thenReturn(new PlayerNotifyConfig(true, true, true, 1000L, 6));
        when(server.server()).thenReturn(bukkitServer);
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(anyString(), anyMap())).thenReturn(MessageEnvelope.publicMessage("ok"));
        doAnswer(inv -> {
                    scheduledTasks.add(inv.getArgument(0));
                    return null;
                })
                .when(server)
                .runLater(any(Runnable.class), anyLong());
        aggregator = new PlayerEventAggregator(server, configs, notifier, formatter);
    }

    private Player mockPlayer(String name) {
        Player p = mock(Player.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getName()).thenReturn(name);
        when(p.getPlayerProfile()).thenReturn(profile);
        when(p.getName()).thenReturn(name);
        when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(p.isOp()).thenReturn(false);
        // 确定性且按名互异的 UUID：KICK→QUIT 去重按 playerId 匹配，不同玩家不可误折叠
        when(p.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
        return p;
    }

    /** 执行最早一个已调度的窗口冲刷任务（模拟调度器在窗口到期后运行）。 */
    private void runTail() {
        assertFalse(scheduledTasks.isEmpty(), "没有待运行的窗口冲刷任务");
        scheduledTasks.poll().run();
    }

    /** 依次执行所有已调度任务（含渲染失败后的重试重调度），直到队列为空。 */
    private void runAllTails() {
        while (!scheduledTasks.isEmpty()) {
            scheduledTasks.poll().run();
        }
    }

    // ---- 单发：窗口内仅 1 条事件，复用原模板 ----

    @Test
    void enqueue_singleJoin_flushRendersPlayerJoin() {
        Player p = mockPlayer("Alice");
        doReturn(List.of(p)).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_join"), anyMap())).thenReturn(MessageEnvelope.publicMessage("ok"));

        aggregator.enqueue(p, PlayerEventService.PlayerState.JOIN);

        // 纯聚合：事件不立即发送，仅调度一次窗口冲刷（1000ms → 20 ticks）
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
        verify(server).runLater(any(Runnable.class), eq(20L));
        runTail();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(configs).renderEvent(eq("player_join"), vars.capture());
        verify(notifier).event(eq("player_join"), any(MessageEnvelope.class));
        Map<String, String> v = vars.getValue();
        // name 沿用 OnlineListFormatter.line()（玩家名 + 游戏模式），与旧 PlayerEventService 行为一致
        assertTrue(v.get("name").startsWith("Alice"), "got: " + v.get("name"));
        assertEquals("1", v.get("online_count"));
        assertEquals("100", v.get("max_count"));
    }

    @Test
    void enqueue_singleQuit_flushUsesLiveOnlineCount() {
        Player quitter = mockPlayer("Alice");
        Player remaining = mockPlayer("Bob");
        // 冲刷时刻当事人已离开在线列表（与事件同步渲染的"减1修正"相反）
        doReturn(List.of(remaining)).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_quit"), anyMap())).thenReturn(MessageEnvelope.publicMessage("ok"));

        aggregator.enqueue(quitter, PlayerEventService.PlayerState.QUIT);
        runTail();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(configs).renderEvent(eq("player_quit"), vars.capture());
        Map<String, String> v = vars.getValue();
        assertEquals("1", v.get("online_count"), "应取冲刷时刻实时在线数，不重复减 1");
    }

    @Test
    void enqueue_singleKick_flushRendersPlayerKick() {
        Player p = mockPlayer("Alice");
        doReturn(List.of()).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_kick"), anyMap())).thenReturn(MessageEnvelope.publicMessage("ok"));

        aggregator.enqueue(p, PlayerEventService.PlayerState.KICK);
        runTail();

        verify(configs).renderEvent(eq("player_kick"), anyMap());
        verify(notifier).event(eq("player_kick"), any(MessageEnvelope.class));
    }

    @Test
    void enqueue_digest_withRankService_sectionLinesIncludeGroupDisplay() {
        // 版块玩家行格式与权限组注入走真实 OnlineListFormatter（缺组名回归保护）
        RankService rankService = mock(RankService.class);
        OnlineListFormatter formatterWithRank = new OnlineListFormatter();
        formatterWithRank.setRankService(rankService);
        aggregator = new PlayerEventAggregator(server, configs, notifier, formatterWithRank);

        Player p1 = mockPlayer("Alice");
        Player p2 = mockPlayer("Bob");
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(p1.getUniqueId()).thenReturn(id1);
        when(p2.getUniqueId()).thenReturn(id2);
        when(rankService.currentGroup(id1)).thenReturn("admin");
        when(rankService.currentGroup(id2)).thenReturn("builder");

        doReturn(List.of(p1, p2)).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_digest"), anyMap())).thenReturn(MessageEnvelope.publicMessage("digest"));

        aggregator.enqueue(p1, PlayerEventService.PlayerState.JOIN);
        aggregator.enqueue(p2, PlayerEventService.PlayerState.JOIN);
        runTail();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(configs).renderEvent(eq("player_digest"), vars.capture());
        String joinSummary = vars.getValue().get("join_summary");
        assertTrue(joinSummary.contains("Alice 生存模式 管理员"), "got: " + joinSummary);
        assertTrue(joinSummary.contains("Bob 生存模式 建造者"), "got: " + joinSummary);
    }

    @Test
    void enqueue_offlineQuitNullLocation_omitsCoordsKeepsName() {
        // 当事人已离线（冲刷时刻坐标不可用）→ 坐标变量省略、world 回退 unknown，名字不受影响
        Player quitter = mockPlayer("Alice");
        when(quitter.getLocation()).thenReturn(null);
        doReturn(List.of()).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_quit"), anyMap())).thenReturn(MessageEnvelope.publicMessage("ok"));

        aggregator.enqueue(quitter, PlayerEventService.PlayerState.QUIT);
        runTail();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(configs).renderEvent(eq("player_quit"), vars.capture());
        Map<String, String> v = vars.getValue();
        assertEquals("unknown", v.get("world"), "离线玩家坐标为 null 时应回退 unknown");
        assertNull(v.get("x_unit"), "坐标缺失时不注入占位值");
        assertTrue(v.get("name").startsWith("Alice"), "got: " + v.get("name"));
    }

    // ---- 多发：窗口内多条事件，渲染聚合摘要（不丢消息，精确计数）----

    @Test
    void enqueue_multipleEvents_flushRendersDigestWithExactCounts() {
        Player a = mockPlayer("Alice");
        Player b = mockPlayer("Bob");
        Player c = mockPlayer("Carol");
        doReturn(List.of(a, b, c)).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_digest"), anyMap())).thenReturn(MessageEnvelope.publicMessage("digest"));

        aggregator.enqueue(a, PlayerEventService.PlayerState.JOIN);
        aggregator.enqueue(b, PlayerEventService.PlayerState.JOIN);
        aggregator.enqueue(c, PlayerEventService.PlayerState.QUIT);
        runTail();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(configs).renderEvent(eq("player_digest"), vars.capture());
        verify(notifier).event(eq("player_digest"), any(MessageEnvelope.class));
        Map<String, String> v = vars.getValue();
        // 版块式摘要：分割线开头（恰好 33 连字符 + 换行）+ 版块头（多人带人数）+ 每人一行；空版块连分割线一并省略
        assertTrue(
                v.get("join_summary").startsWith("---------------------------------\n"),
                "got: " + v.get("join_summary"));
        assertTrue(v.get("join_summary").contains("🥰 上线(2)："), "got: " + v.get("join_summary"));
        assertTrue(v.get("join_summary").contains("Alice"), "got: " + v.get("join_summary"));
        assertTrue(v.get("join_summary").contains("Bob"), "got: " + v.get("join_summary"));
        assertTrue(
                v.get("quit_summary").startsWith("---------------------------------\n"),
                "got: " + v.get("quit_summary"));
        assertTrue(v.get("quit_summary").contains("😋 下线："), "单人版块不显示人数, got: " + v.get("quit_summary"));
        assertEquals("", v.get("kick_summary"), "空版块整段（含分割线）省略");
        assertEquals("3", v.get("online_count"));
    }

    @Test
    void enqueue_manyEvents_truncatesNamesOnlyCountExact() {
        when(configs.playerNotify()).thenReturn(new PlayerNotifyConfig(true, true, true, 1000L, 6));
        doReturn(List.of()).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_digest"), anyMap())).thenReturn(MessageEnvelope.publicMessage("digest"));

        for (int i = 0; i < 10; i++) {
            aggregator.enqueue(mockPlayer("P" + i), PlayerEventService.PlayerState.JOIN);
        }
        runTail();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(configs).renderEvent(eq("player_digest"), vars.capture());
        String joinSummary = vars.getValue().get("join_summary");
        // 计数精确（+10），名称仅显示前 6 个并截断
        assertTrue(joinSummary.contains("🥰 上线(10)："), "got: " + joinSummary);
        assertTrue(joinSummary.contains("P0") && joinSummary.contains("P5"), "got: " + joinSummary);
        assertFalse(joinSummary.contains("P6"), "P6 应被截断: " + joinSummary);
        assertTrue(joinSummary.contains("等4人"), "got: " + joinSummary);
    }

    // ---- 限流上界与窗口行为 ----

    @Test
    void enqueue_multipleWithinWindow_singleScheduledFlush() {
        aggregator.enqueue(mockPlayer("A"), PlayerEventService.PlayerState.JOIN);
        aggregator.enqueue(mockPlayer("B"), PlayerEventService.PlayerState.JOIN);
        aggregator.enqueue(mockPlayer("C"), PlayerEventService.PlayerState.QUIT);

        verify(server, times(1)).runLater(any(Runnable.class), anyLong());
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
    }

    @Test
    void enqueue_windowMs_convertsToTicks() {
        when(configs.playerNotify()).thenReturn(new PlayerNotifyConfig(true, true, true, 5000L, 6));

        aggregator.enqueue(mockPlayer("A"), PlayerEventService.PlayerState.JOIN);

        verify(server).runLater(any(Runnable.class), eq(100L)); // 5000ms / 50
    }

    @Test
    void enqueue_configReloaded_usesNewWindow() {
        // 首个窗口 1000ms → 20 ticks
        aggregator.enqueue(mockPlayer("A"), PlayerEventService.PlayerState.JOIN);
        runTail();

        // 热重载：新窗口 5000ms → 100 ticks，不重建 service
        when(configs.playerNotify()).thenReturn(new PlayerNotifyConfig(true, true, true, 5000L, 6));
        aggregator.enqueue(mockPlayer("B"), PlayerEventService.PlayerState.JOIN);

        verify(server).runLater(any(Runnable.class), eq(100L));
    }

    @Test
    void enqueue_disabledState_suppressed() {
        when(configs.playerNotify()).thenReturn(new PlayerNotifyConfig(false, true, true, 1000L, 6));

        aggregator.enqueue(mockPlayer("Alice"), PlayerEventService.PlayerState.JOIN);

        verify(server, never()).runLater(any(Runnable.class), anyLong());
        verifyNoInteractions(notifier);
    }

    // ---- 冲刷时按最新配置过滤（热重载对挂起批次生效）----

    @Test
    void flush_configDisabledMidWindow_suppressesBufferedEvents() {
        aggregator.enqueue(mockPlayer("A"), PlayerEventService.PlayerState.JOIN);
        aggregator.enqueue(mockPlayer("B"), PlayerEventService.PlayerState.JOIN);
        // 窗口内管理员关闭上线通知 → 挂起批次不再发送
        when(configs.playerNotify()).thenReturn(new PlayerNotifyConfig(false, true, true, 1000L, 6));

        runTail();

        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
        verify(configs, never()).renderEvent(anyString(), anyMap());
    }

    @Test
    void flush_configDisabledMidWindow_singleRemainingUsesSingleTemplate() {
        Player b = mockPlayer("B");
        aggregator.enqueue(mockPlayer("A"), PlayerEventService.PlayerState.JOIN);
        aggregator.enqueue(b, PlayerEventService.PlayerState.QUIT);
        // 上线被关闭后，窗口内剩余 1 条 QUIT → 走单条模板而非摘要
        when(configs.playerNotify()).thenReturn(new PlayerNotifyConfig(false, true, true, 1000L, 6));
        doReturn(List.of(b)).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_quit"), anyMap())).thenReturn(MessageEnvelope.publicMessage("quit"));

        runTail();

        verify(configs).renderEvent(eq("player_quit"), anyMap());
        verify(configs, never()).renderEvent(eq("player_digest"), anyMap());
        verify(notifier).event(eq("player_quit"), any(MessageEnvelope.class));
    }

    // ---- KICK→QUIT 去重：真实 Paper 一次踢出触发 PlayerKickEvent + PlayerQuitEvent ----

    @Test
    void enqueue_kickThenQuitSamePlayer_collapsesToSingleKick() {
        Player p = mockPlayer("Alice");
        doReturn(List.of()).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_kick"), anyMap())).thenReturn(MessageEnvelope.publicMessage("kick"));

        // 真实 Paper 下成功的踢出会额外触发 PlayerQuitEvent（同一离开上报两次）
        aggregator.enqueue(p, PlayerEventService.PlayerState.KICK);
        aggregator.enqueue(p, PlayerEventService.PlayerState.QUIT);
        runTail();

        // 折叠后窗口内仅 1 条 → 单发走 player_kick 模板，不出现双计摘要
        verify(configs).renderEvent(eq("player_kick"), anyMap());
        verify(configs, never()).renderEvent(eq("player_digest"), anyMap());
        verify(notifier).event(eq("player_kick"), any(MessageEnvelope.class));
    }

    @Test
    void enqueue_kickAndQuit_differentPlayers_countsBothInDigest() {
        Player kicked = mockPlayer("Alice");
        Player quitter = mockPlayer("Bob");
        doReturn(List.of()).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_digest"), anyMap())).thenReturn(MessageEnvelope.publicMessage("digest"));

        aggregator.enqueue(kicked, PlayerEventService.PlayerState.KICK);
        aggregator.enqueue(quitter, PlayerEventService.PlayerState.QUIT);
        runTail();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(configs).renderEvent(eq("player_digest"), vars.capture());
        Map<String, String> v = vars.getValue();
        assertTrue(v.get("kick_summary").contains("😂 被踢："), "got: " + v.get("kick_summary"));
        assertTrue(v.get("quit_summary").contains("😋 下线："), "got: " + v.get("quit_summary"));
    }

    // ---- 禁用/重载同步冲刷：不走调度器，避免尾部任务被取消导致整窗丢失 ----

    @Test
    void flushPending_flushesSynchronously_leftoverScheduledTaskIsNoop() {
        Player p = mockPlayer("Alice");
        doReturn(List.of(p)).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_join"), anyMap())).thenReturn(MessageEnvelope.publicMessage("ok"));

        aggregator.enqueue(p, PlayerEventService.PlayerState.JOIN);
        // 禁用场景：不推进调度器，直接同步冲刷
        aggregator.flushPending();

        verify(notifier).event(eq("player_join"), any(MessageEnvelope.class));
        // 已交付，入队时调度的尾部任务再执行应为空操作（不重复发送）
        runTail();
        verify(notifier, times(1)).event(anyString(), any(MessageEnvelope.class));
    }

    @Test
    void flushPending_renderFailure_logsSevereWithoutReschedule() {
        doThrow(new IllegalStateException("template broken")).when(configs).renderEvent(anyString(), anyMap());
        aggregator.enqueue(mockPlayer("A"), PlayerEventService.PlayerState.JOIN);

        aggregator.flushPending();

        verify(logger).severe(anyString());
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
        // 禁用场景失败不重排：仅告警（只有入队时的 1 次调度）
        verify(server, times(1)).runLater(any(Runnable.class), anyLong());
    }

    // ---- 渲染失败：保留批次重试（有界），不静默丢弃整窗 ----

    @Test
    void flush_renderFailure_retainsBatchAndRetries_transientRecovery() {
        doThrow(new IllegalStateException("template broken")).when(configs).renderEvent(anyString(), anyMap());
        aggregator.enqueue(mockPlayer("A"), PlayerEventService.PlayerState.JOIN);

        // 首次冲刷渲染失败：不再抛出，批次保留并重试调度
        runTail();
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
        assertFalse(scheduledTasks.isEmpty(), "失败后应重试调度");

        // 渲染恢复后重试成功，事件不丢失
        doReturn(MessageEnvelope.publicMessage("ok")).when(configs).renderEvent(anyString(), anyMap());
        runTail();
        verify(notifier).event(eq("player_join"), any(MessageEnvelope.class));
        assertTrue(scheduledTasks.isEmpty(), "成功后不应再调度");
    }

    @Test
    void flush_renderFailure_boundedRetries_dropsAfterCapWithAlert() {
        doThrow(new IllegalStateException("template broken")).when(configs).renderEvent(anyString(), anyMap());
        aggregator.enqueue(mockPlayer("A"), PlayerEventService.PlayerState.JOIN);

        // MAX_RENDER_RETRIES=3 → 第 4 次冲刷后放弃：1 首冲 + 3 重试
        runAllTails();

        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
        verify(logger).severe(anyString());

        // 有界放弃后批次已清空，新事件可正常聚合（无孤儿批次）
        doReturn(MessageEnvelope.publicMessage("ok")).when(configs).renderEvent(anyString(), anyMap());
        aggregator.enqueue(mockPlayer("B"), PlayerEventService.PlayerState.JOIN);
        runTail();
        verify(notifier).event(eq("player_join"), any(MessageEnvelope.class));
    }

    // ---- Folia 并发：region 线程并发入队被锁串行化 → 单一批次、计数精确 ----

    @Test
    void concurrentEnqueue_serializedIntoOneBatch_exactCounts() throws Exception {
        when(configs.playerNotify()).thenReturn(new PlayerNotifyConfig(true, true, true, 1000L, 6));
        doReturn(List.of()).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(100);
        when(configs.renderEvent(eq("player_digest"), anyMap())).thenReturn(MessageEnvelope.publicMessage("digest"));

        // Folia 下不同玩家的 JOIN 事件在各自 chunk 的 region 线程触发，可并发入队。
        // synchronized 必须把所有事件串行收进同一批次，恰好一次窗口调度、摘要计数精确无丢失。
        int threads = 50;
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            players.add(mockPlayer("P" + i));
        }
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (Player p : players) {
            pool.submit(() -> {
                try {
                    barrier.await();
                } catch (InterruptedException | java.util.concurrent.BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
                aggregator.enqueue(p, PlayerEventService.PlayerState.JOIN);
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "并发入队应在超时内完成");

        verify(server, times(1)).runLater(any(Runnable.class), anyLong());
        runTail();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(configs).renderEvent(eq("player_digest"), vars.capture());
        assertTrue(
                vars.getValue().get("join_summary").contains("🥰 上线(" + threads + ")："),
                "got: " + vars.getValue().get("join_summary"));
    }
}
