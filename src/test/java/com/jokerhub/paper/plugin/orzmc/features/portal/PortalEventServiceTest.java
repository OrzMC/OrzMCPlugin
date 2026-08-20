package com.jokerhub.paper.plugin.orzmc.features.portal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalPort;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PortalEventService 测试：PlayerPortalEvent（Paper 路径）+ PlayerMoveEvent（Folia 补偿路径）。
 */
class PortalEventServiceTest extends ServiceTestBase {

    private ServerFacade server;
    private PortalPort portalService;
    private World world;
    private Player player;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        server = mock(ServerFacade.class);
        portalService = mock(PortalPort.class);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        player = mock(Player.class);
        uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.isOnline()).thenReturn(true);
        // transfer 经 runSync 派发：让 lambda 实际执行，并默认命令派发成功
        when(server.executeConsoleCommand(anyString()))
                .thenReturn(
                        new ServerFacade.ConsoleCommandResult("transfer 127.0.0.1 25566 TestPlayer", true, List.of()));
        doAnswer(invocation -> {
                    ((Runnable) invocation.getArgument(0)).run();
                    return null;
                })
                .when(server)
                .runSync(any());
    }

    private Location loc(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    // ---- Paper 路径：PlayerPortalEvent（带邻域容差的 findTarget）----

    @Test
    void portalEvent_matchingTarget_cancelsAndTransfers() {
        when(portalService.findTarget(any(Location.class))).thenReturn("127.0.0.1:25566");
        PortalEventService service = new PortalEventService(server, portalService);

        PlayerPortalEvent event = new PlayerPortalEvent(
                player, loc(100, 64, 100), loc(100, 64, 100), TeleportCause.NETHER_PORTAL, 1, true, 1);
        service.handle(event);

        assertTrue(event.isCancelled());
        verify(server).executeConsoleCommand("transfer 127.0.0.1 25566 TestPlayer");
    }

    @Test
    void portalEvent_noTarget_letsVanillaProceed() {
        when(portalService.findTarget(any(Location.class))).thenReturn(null);
        PortalEventService service = new PortalEventService(server, portalService);

        PlayerPortalEvent event = new PlayerPortalEvent(
                player, loc(100, 64, 100), loc(100, 64, 100), TeleportCause.NETHER_PORTAL, 1, true, 1);
        service.handle(event);

        assertFalse(event.isCancelled());
        verify(server, never()).executeConsoleCommand(anyString());
    }

    // ---- Folia 补偿路径：PlayerMoveEvent（精确命中 findTargetExact）----

    @Test
    void moveEvent_paperMode_ignored() {
        // Paper 上仅 PlayerPortalEvent 生效，move 检测不注册逻辑
        PortalEventService service = new PortalEventService(server, portalService, false);
        PlayerMoveEvent event = new PlayerMoveEvent(player, loc(100, 64, 100), loc(101, 64, 100));

        service.handleMove(event);

        verify(portalService, never()).findTargetExact(any());
        verify(server, never()).executeConsoleCommand(anyString());
    }

    @Test
    void moveEvent_foliaMode_walkingIntoPortal_transfers() {
        when(portalService.findTargetExact(any(Location.class))).thenReturn("127.0.0.1:25566");
        PortalEventService service = new PortalEventService(server, portalService, true);
        // 从传送门外（100,64,100）走进传送门方块（101,64,100）
        PlayerMoveEvent event = new PlayerMoveEvent(player, loc(100, 64, 100), loc(101, 64, 100));

        service.handleMove(event);

        verify(portalService).findTargetExact(loc(101, 64, 100));
        verify(server).executeConsoleCommand("transfer 127.0.0.1 25566 TestPlayer");
    }

    @Test
    void moveEvent_groundPortal_feetBelowInterior_torsoTriggers() {
        // G-1 几何回归：地面传送门内部格从脚底+1 起（PortalBuilder cy=baseY+2，内部 y=baseY+1~+3），
        // 玩家穿门时脚底格（baseY）精确命中失败，须由躯干格（脚+1）命中 → transfer
        when(portalService.findTargetExact(loc(101, 64, 100))).thenReturn(null);
        when(portalService.findTargetExact(loc(101, 65, 100))).thenReturn("127.0.0.1:25566");
        PortalEventService service = new PortalEventService(server, portalService, true);
        // 脚底 (101,64,100) = 地面层；躯干 (101,65,100) = 内部格底层
        PlayerMoveEvent event = new PlayerMoveEvent(player, loc(100, 64, 100), loc(101, 64, 100));

        service.handleMove(event);

        verify(portalService).findTargetExact(loc(101, 64, 100));
        verify(portalService).findTargetExact(loc(101, 65, 100));
        verify(server).executeConsoleCommand("transfer 127.0.0.1 25566 TestPlayer");
    }

    @Test
    void moveEvent_adjacentBlock_passesByPortal_noTransfer() {
        // G-A: move 路径精确命中——贴着传送门 1 格路过（findTarget 邻域容差会命中，findTargetExact 不会）→ 不误触发
        when(portalService.findTarget(any(Location.class))).thenReturn("127.0.0.1:25566");
        PortalEventService service = new PortalEventService(server, portalService, true);
        PlayerMoveEvent event = new PlayerMoveEvent(player, loc(101, 64, 100), loc(102, 64, 100));

        service.handleMove(event);

        verify(portalService).findTargetExact(loc(102, 64, 100));
        verify(server, never()).executeConsoleCommand(anyString());
    }

    @Test
    void moveEvent_standingInPortal_noBlockChange_skipped() {
        // 只有方块坐标变化才算走进；站在传送门内不动（例如只转头）不触发
        PortalEventService service = new PortalEventService(server, portalService, true);
        PlayerMoveEvent event = new PlayerMoveEvent(player, loc(101, 64, 100), loc(101.1, 64, 100));

        service.handleMove(event);

        verify(portalService, never()).findTargetExact(any());
        verify(server, never()).executeConsoleCommand(anyString());
    }

    @Test
    void moveEvent_outsidePortal_noTransfer() {
        when(portalService.findTargetExact(any(Location.class))).thenReturn(null);
        PortalEventService service = new PortalEventService(server, portalService, true);
        PlayerMoveEvent event = new PlayerMoveEvent(player, loc(10, 64, 10), loc(11, 64, 10));

        service.handleMove(event);

        verify(server, never()).executeConsoleCommand(anyString());
    }

    @Test
    void moveEvent_cancelledByOtherPlugin_ignored() {
        // G3: 反作弊/区域防护插件取消本次移动 → 不按「意图位置」误触发 transfer
        when(portalService.findTargetExact(any(Location.class))).thenReturn("127.0.0.1:25566");
        PortalEventService service = new PortalEventService(server, portalService, true);
        PlayerMoveEvent event = new PlayerMoveEvent(player, loc(100, 64, 100), loc(101, 64, 100));
        event.setCancelled(true);

        service.handleMove(event);

        verify(portalService, never()).findTargetExact(any());
        verify(server, never()).executeConsoleCommand(anyString());
    }

    @Test
    void moveEvent_cooldown_skipsRepeatedTransfer() {
        when(portalService.findTargetExact(any(Location.class))).thenReturn("127.0.0.1:25566");
        PortalEventService service = new PortalEventService(server, portalService, true);
        service.handleMove(new PlayerMoveEvent(player, loc(100, 64, 100), loc(101, 64, 100)));
        verify(server, times(1)).executeConsoleCommand(anyString());

        // 5 秒冷却内再次移动（方块变化）→ 不重复 transfer
        PlayerMoveEvent second = new PlayerMoveEvent(player, loc(101, 64, 100), loc(102, 64, 100));
        service.handleMove(second);

        verify(server, times(1)).executeConsoleCommand(anyString());
    }

    @Test
    void portalEvent_cooldown_sharedWithMovePath() {
        // G4: 冷却为双路径共享——move 路径刚 transfer 过，5s 内 portal 路径不再重复 transfer；
        // 且冷却内不接管事件（不取消原版传送），保证「取消 ⇔ transfer」自洽
        when(portalService.findTargetExact(any(Location.class))).thenReturn("127.0.0.1:25566");
        when(portalService.findTarget(any(Location.class))).thenReturn("127.0.0.1:25566");
        PortalEventService service = new PortalEventService(server, portalService, true);
        service.handleMove(new PlayerMoveEvent(player, loc(100, 64, 100), loc(101, 64, 100)));
        verify(server, times(1)).executeConsoleCommand(anyString());

        PlayerPortalEvent portalEvent = new PlayerPortalEvent(
                player, loc(101, 64, 100), loc(101, 64, 100), TeleportCause.NETHER_PORTAL, 1, true, 1);
        service.handle(portalEvent);

        assertFalse(portalEvent.isCancelled());
        verify(server, times(1)).executeConsoleCommand(anyString());
    }

    @Test
    void portalEvent_unauthenticated_cancelsEvent() {
        // 未认证玩家触发传送门 → 取消原版传送（不放行穿门），不 transfer
        when(portalService.findTarget(any(Location.class))).thenReturn("127.0.0.1:25566");
        PortalEventService service = new PortalEventService(server, portalService, false, p -> false);

        PlayerPortalEvent event = new PlayerPortalEvent(
                player, loc(100, 64, 100), loc(100, 64, 100), TeleportCause.NETHER_PORTAL, 1, true, 1);
        service.handle(event);

        assertTrue(event.isCancelled());
        verify(server, never()).executeConsoleCommand(anyString());
    }

    @Test
    void moveEvent_unauthenticated_skipped() {
        // 未认证玩家走进传送门 → 不 transfer（登录插件自行保护）
        when(portalService.findTargetExact(any(Location.class))).thenReturn("127.0.0.1:25566");
        PortalEventService service = new PortalEventService(server, portalService, true, p -> false);

        service.handleMove(new PlayerMoveEvent(player, loc(100, 64, 100), loc(101, 64, 100)));

        verify(server, never()).executeConsoleCommand(anyString());
    }

    @Test
    void moveEvent_afterCooldown_transfersAgain() {
        when(portalService.findTargetExact(any(Location.class))).thenReturn("127.0.0.1:25566");
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(0);
        PortalEventService service = new PortalEventService(server, portalService, true, p -> true, clock::get);
        service.handleMove(new PlayerMoveEvent(player, loc(100, 64, 100), loc(101, 64, 100)));
        verify(server, times(1)).executeConsoleCommand(anyString());

        // 冷却（5s）过期后再次走进传送门 → 允许再次 transfer（假时钟推进，无需真实 sleep）
        clock.set(6000);
        service.handleMove(new PlayerMoveEvent(player, loc(101, 64, 100), loc(102, 64, 100)));
        verify(server, times(2)).executeConsoleCommand(anyString());
    }

    @Test
    void transfer_failedCommand_logsWarning() {
        // G-B: transfer 命令派发失败（目标服不可达）→ WARNING 日志，不再静默
        when(portalService.findTargetExact(any(Location.class))).thenReturn("127.0.0.1:25566");
        when(server.executeConsoleCommand(anyString()))
                .thenReturn(new ServerFacade.ConsoleCommandResult(
                        "transfer 127.0.0.1 25566 TestPlayer", false, List.of("无法连接到目标服务器")));
        PortalEventService service = new PortalEventService(server, portalService, true);

        java.util.logging.Logger log = java.util.logging.Logger.getLogger("OrzMC.PortalEvent");
        List<String> records = new java.util.ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                records.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        log.addHandler(handler);
        try {
            service.handleMove(new PlayerMoveEvent(player, loc(100, 64, 100), loc(101, 64, 100)));

            assertTrue(records.stream().anyMatch(m -> m != null && m.contains("跨服 transfer 命令执行失败")));
        } finally {
            log.removeHandler(handler);
        }
    }
}
