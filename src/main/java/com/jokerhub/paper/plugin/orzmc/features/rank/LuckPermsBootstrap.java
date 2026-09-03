package com.jokerhub.paper.plugin.orzmc.features.rank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.track.Track;

/**
 * 权限自动初始化（启动时幂等执行）——「装即用」：安装/更新插件后无需手动创建
 * track 或权限组，LP 可用时自动补齐骨架。
 *
 * <p>设计原则：<b>继承链与 track 链序由插件保证正确，权限节点由线上自管</b>——
 * <ul>
 *   <li>track「rank」缺失 → 创建（default→member→builder→admin）；已存在但链序不一致
 *       → 重建校正（rank 晋升链是插件功能依赖，必须正确）</li>
 *   <li>组 member/builder/admin 缺失 → 创建并按继承链挂 parent
 *       （member→default、builder→member、admin→builder）；已存在但继承关系与设计不符
 *       → 校正 parent（清除现有继承节点、按设计重挂——<b>只动继承，不碰任何权限节点</b>，
 *       组内其它权限完全以线上定义为准）</li>
 * </ul>
 *
 * <p>不内置具体权限节点：新组继承链最终落到 default 组，基础权限由各服 default
 * 组定义，天然适配不同服务器的插件集。无 LP 时本类不执行
 * （由 {@link NoopRankPromoter} 降级，权限功能不可用但插件其余功能正常）。</p>
 */
public final class LuckPermsBootstrap {

    /** track「rank」的链序（与 {@link LuckPermsPromoter#TRACK} 对应）。 */
    private static final List<String> TRACK_GROUPS = List.of("default", "member", "builder", "admin");

    /** 继承链（子组 → 父组），与 TRACK_GROUPS 的相邻递进一致。 */
    private static final List<String[]> INHERITANCE = List.of(
            new String[] {"member", "default"}, new String[] {"builder", "member"}, new String[] {"admin", "builder"});

    /**
     * 坐牢组（完全独立于四级 track，见 {@code features.prison.PrisonLpGateway#PRISON_GROUP}）。
     *
     * <p>不参与继承链（无 parent）、不在 track「rank」；只给 {@code essentials.msg}（私聊）权限，
     * 其余全部禁言——作弊玩家进牢房后无法使用任何命令，但可私聊申诉/沟通。</p>
     */
    private static final String PRISON_GROUP = "prison";

    private final LuckPerms api;
    private final Logger logger;

    public LuckPermsBootstrap(LuckPerms api, Logger logger) {
        this.api = api;
        this.logger = logger;
    }

    /** 启动初始化：先补四级组（含继承校正）、再补 prison 坐牢组、再补 track（幂等）。 */
    public void initialize() {
        ensureGroups().thenCompose(v -> ensurePrisonGroup()).thenRun(this::ensureTrack);
    }

    /** 确保 prison 坐牢组存在且带 essentials.msg 权限（幂等；缺失建组，已存在只补权限，不动其它）。 */
    private CompletableFuture<Void> ensurePrisonGroup() {
        Group existing = api.getGroupManager().getGroup(PRISON_GROUP);
        if (existing == null) {
            return createPrisonGroup();
        }
        return ensurePrisonPermission(existing);
    }

    private CompletableFuture<Void> createPrisonGroup() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        api.getGroupManager()
                .createAndLoadGroup(PRISON_GROUP)
                .thenCompose(group -> {
                    group.data()
                            .add(api.getNodeBuilderRegistry()
                                    .forPermission()
                                    .permission("essentials.msg")
                                    .build());
                    return api.getGroupManager().saveGroup(group);
                })
                .whenComplete((v, err) -> {
                    if (err != null) {
                        logger.warning("[OrzMC] 权限初始化：创建 prison 坐牢组失败 - " + err);
                    } else {
                        logger.info("[OrzMC] 权限初始化：已创建 prison 坐牢组（独立组，仅 essentials.msg）");
                    }
                    done.complete(null); // 组失败不阻塞后续（track 建链时跳过缺失组）
                });
        return done;
    }

    /** 已存在 prison 组时只保证 essentials.msg 权限存在（幂等，不覆盖线上其它权限）。 */
    private CompletableFuture<Void> ensurePrisonPermission(Group group) {
        for (Node node : group.data().toCollection()) {
            if (node instanceof PermissionNode pn && "essentials.msg".equals(pn.getPermission())) {
                return CompletableFuture.completedFuture(null);
            }
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        group.data()
                .add(api.getNodeBuilderRegistry()
                        .forPermission()
                        .permission("essentials.msg")
                        .build());
        api.getGroupManager().saveGroup(group).whenComplete((v, err) -> {
            if (err != null) {
                logger.warning("[OrzMC] 权限初始化：prison 组补 essentials.msg 失败 - " + err);
            }
            done.complete(null);
        });
        return done;
    }

    private CompletableFuture<Void> ensureGroups() {
        CompletableFuture<?>[] futures = INHERITANCE.stream()
                .map(chain -> ensureGroup(chain[0], chain[1]))
                .toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(futures);
    }

    private void ensureTrack() {
        Track existing = api.getTrackManager().getTrack(LuckPermsPromoter.TRACK);
        if (existing == null) {
            createTrack();
            return;
        }
        List<String> current = existing.getGroups();
        if (current.equals(TRACK_GROUPS)) {
            return;
        }
        logger.info("[OrzMC] 权限初始化：track「rank」链序不一致（" + String.join("→", current) + "），重建为 "
                + String.join("→", TRACK_GROUPS));
        api.getTrackManager().deleteTrack(existing).thenRun(this::createTrack);
    }

    private void createTrack() {
        api.getTrackManager()
                .createAndLoadTrack(LuckPermsPromoter.TRACK)
                .thenCompose(track -> {
                    for (String groupName : TRACK_GROUPS) {
                        Group group = api.getGroupManager().getGroup(groupName);
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
        Group existing = api.getGroupManager().getGroup(name);
        if (existing == null) {
            return createGroup(name, parentName);
        }
        return ensureParent(existing, name, parentName);
    }

    private CompletableFuture<Void> createGroup(String name, String parentName) {
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

    /** 校正已有组的继承关系：只动继承节点，绝不触碰权限节点（线上权限由线上自管）。 */
    private CompletableFuture<Void> ensureParent(Group group, String name, String expectedParent) {
        Set<String> current = new HashSet<>();
        for (Node node : group.data().toCollection()) {
            if (node instanceof InheritanceNode inheritance) {
                current.add(inheritance.getGroupName());
            }
        }
        if (current.size() == 1 && current.contains(expectedParent)) {
            return CompletableFuture.completedFuture(null);
        }
        logger.info("[OrzMC] 权限初始化：校正组「" + name + "」继承 " + (current.isEmpty() ? "(无)" : String.join(",", current))
                + " -> " + expectedParent);
        CompletableFuture<Void> done = new CompletableFuture<>();
        group.data().clear(node -> node instanceof InheritanceNode);
        group.data()
                .add(api.getNodeBuilderRegistry()
                        .forInheritance()
                        .group(expectedParent)
                        .build());
        api.getGroupManager().saveGroup(group).whenComplete((v, err) -> {
            if (err != null) {
                logger.warning("[OrzMC] 权限初始化：校正组「" + name + "」继承失败 - " + err);
            }
            done.complete(null);
        });
        return done;
    }
}
