package com.jokerhub.paper.plugin.orzmc.features.rank;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;

/**
 * 权限自动初始化（启动时幂等执行）——「装即用」：安装/更新插件后无需手动创建
 * track 或权限组，LP 可用时自动补齐骨架。
 *
 * <p>设计原则：<b>只补缺失，绝不覆盖已有</b>——
 * <ul>
 *   <li>track「rank」缺失 → 创建（default→member→builder→admin）；已有 → 跳过
 *       （链序由管理员维护，插件不强制改写）</li>
 *   <li>组 member/builder/admin 缺失 → 创建并按继承链挂 parent
 *       （member→default、builder→member、admin→builder）；已有 → 跳过
 *       （组权限内容/继承以线上定义为准，插件不覆盖）</li>
 * </ul>
 *
 * <p>不内置具体权限节点：新组继承链最终落到 default 组，基础权限由各服 default
 * 组定义，天然适配不同服务器的插件集。无 LP 时本类不执行
 * （由 {@link NoopRankPromoter} 降级，权限功能不可用但插件其余功能正常）。</p>
 */
public final class LuckPermsBootstrap {

    /** track「rank」的链序（与 {@link LuckPermsPromoter#TRACK} 对应）。 */
    private static final List<String> TRACK_GROUPS = List.of("default", "member", "builder", "admin");

    /** 新建组的继承链（子组 → 父组）。 */
    private static final List<String[]> INHERITANCE = List.of(
            new String[] {"member", "default"}, new String[] {"builder", "member"}, new String[] {"admin", "builder"});

    private final LuckPerms api;
    private final Logger logger;

    public LuckPermsBootstrap(LuckPerms api, Logger logger) {
        this.api = api;
        this.logger = logger;
    }

    /** 启动初始化：先补组、再补 track（track 链引用组对象，须组先就绪；幂等，已有不覆盖）。 */
    public void initialize() {
        ensureGroups().thenRun(this::ensureTrack);
    }

    private CompletableFuture<Void> ensureGroups() {
        CompletableFuture<?>[] futures = INHERITANCE.stream()
                .map(chain -> ensureGroup(chain[0], chain[1]))
                .toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(futures);
    }

    private void ensureTrack() {
        if (api.getTrackManager().getTrack(LuckPermsPromoter.TRACK) != null) {
            return;
        }
        api.getTrackManager()
                .createAndLoadTrack(LuckPermsPromoter.TRACK)
                .thenCompose(track -> {
                    for (String groupName : TRACK_GROUPS) {
                        net.luckperms.api.model.group.Group group =
                                api.getGroupManager().getGroup(groupName);
                        if (group != null) {
                            track.appendGroup(group);
                        }
                    }
                    return api.getTrackManager().saveTrack(track);
                })
                .whenComplete((v, err) -> {
                    if (err != null) {
                        logger.warning("[OrzMC] 权限初始化：创建 track「rank」失败 - " + err);
                    } else {
                        logger.info("[OrzMC] 权限初始化：已创建 track「rank」（" + String.join("→", TRACK_GROUPS) + "）");
                    }
                });
    }

    private CompletableFuture<Void> ensureGroup(String name, String parentName) {
        if (api.getGroupManager().getGroup(name) != null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        api.getGroupManager()
                .createAndLoadGroup(name)
                .thenCompose(group -> {
                    group.data()
                            .add(api.getNodeBuilderRegistry()
                                    .forInheritance()
                                    .group(parentName)
                                    .build());
                    return api.getGroupManager().saveGroup(group);
                })
                .whenComplete((v, err) -> {
                    if (err != null) {
                        logger.warning("[OrzMC] 权限初始化：创建组「" + name + "」失败 - " + err);
                    } else {
                        logger.info("[OrzMC] 权限初始化：已创建组「" + name + "」（继承 " + parentName + "）");
                    }
                    done.complete(null); // 组失败不阻塞后续（track 建链时跳过缺失组）
                });
        return done;
    }
}
