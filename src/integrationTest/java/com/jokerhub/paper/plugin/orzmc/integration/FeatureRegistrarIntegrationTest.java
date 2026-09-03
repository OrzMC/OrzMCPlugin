package com.jokerhub.paper.plugin.orzmc.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * #243 拆出的逐特性注册器回归网：apply/review/rank/prison/update 五个此前零覆盖的
 * 被移命令，在 MockBukkit 全量装配下真实 dispatch，验证命令树未在搬移中断线。
 *
 * <p>覆盖策略：开放命令（apply/rank）用普通玩家走 PlayerOnly+PrisonDeny 链；
 * admin 命令（review/prison/update）用 op 玩家走 AdminOnly 链。同步路径断言
 * 玩家确实收到消息（能抓住 delegate 漏接/参数错位），纯异步路径（/review approve）
 * 仅做 no-throw 冒烟避免调度时序抖动。</p>
 */
@Tag("integration")
public class FeatureRegistrarIntegrationTest {

    private ServerMock server;
    private OrzMC plugin;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OrzMC.class);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    // ---- 开放命令（普通玩家，走 PlayerOnly + PrisonDeny 拦截链）----

    @Test
    public void applyNoArgs_repliesTypeListToPlayer() {
        PlayerMock player = server.addPlayer();

        assertDoesNotThrow(() -> server.dispatchCommand(player, "apply"));

        assertNotNull(player.nextComponentMessage(), "/apply 应同步回显可申请类型列表");
    }

    @Test
    public void rankAdminArg_unknownTarget_repliesError() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        // 注：/rank 无参查询（玩家自己状态）会深触 PermissionStore 真实世界文件夹，
        // MockBukkit WorldMock 不支持——该深路径由 RankCommandServiceTest 覆盖，此处
        // 只验 admin <玩家> 分支接线（参数解析 + 未知玩家错误回显，不碰 world folder）。
        assertDoesNotThrow(() -> server.dispatchCommand(player, "rank AbsolutelyNoSuchPlayer123"));

        assertNotNull(player.nextComponentMessage(), "/rank <不存在玩家> 应回显错误");
    }

    // ---- admin 命令（op 玩家，走 AdminOnly 链）----

    @Test
    public void prisonNoArgs_repliesUsageToAdmin() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertDoesNotThrow(() -> server.dispatchCommand(player, "prison"));

        assertNotNull(player.nextComponentMessage(), "/prison 应回显用法（同步）");
    }

    @Test
    public void updateNoArgs_repliesUsageToAdmin_noNetwork() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertDoesNotThrow(() -> server.dispatchCommand(player, "update"));

        assertNotNull(player.nextComponentMessage(), "/update 用法回显不应触发网络调用");
    }

    @Test
    public void reviewApprove_unknownTarget_smokeNoThrow() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        // 未知玩家 → 审核失败路径；结果经异步渲染，仅验证接线无异常（参数/子命令拼写正确）
        assertDoesNotThrow(() -> server.dispatchCommand(player, "review approve DefinitelyNotARealPlayer123"));
    }
}
