package com.jokerhub.paper.plugin.orzmc.features.rank;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.RankColorsConfig;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * 玩家名颜色服务（按权限等级，三处统一着色）。
 *
 * <p>权限等级来自现有 LuckPerms track（default→member→builder→admin，{@link RankService}），
 * 在三个界面按等级给玩家名着色：头顶名牌（计分板队伍颜色）、聊天消息、Tab 列表。
 * 纯颜色区分，不加前缀文字。聊天/Tab 的名字用 {@code displayName} 纯文本（保留
 * EssentialsX {@code /nick} 等昵称）强制 rank 色；头顶名牌受计分板 team entry 限制
 * 只能用真实名着色（与 vanilla+EssentialsX 自身行为一致）。</p>
 *
 * <p>{@code nametag_enabled} / {@code tab_enabled} 可独立开关头顶名牌与 Tab 着色
 * （默认均开）；总开关 {@code enabled} 或 {@code tab_enabled} 关闭时 Tab 名
 * {@code playerListName} 置空，交由 client 计分板队伍/vanilla 渲染（恢复原 team 前缀、
 * 真实名），聊天着色不受影响。</p>
 *
 * <p>OP 与四级权限是独立体系：{@code player.isOp()} 优先显示 {@code op_color}，与等级组无关。</p>
 *
 * <p><b>冲突感知（只动自家队伍）</b>：只创建/管理 {@code orzmc-*} 队伍，绝不删改外部队伍；
 * 若某玩家的 entry 已被非 orzmc 队伍占用（其它插件接管名牌），主动让位跳过头顶着色，
 * 聊天+Tab 照常；全程不触碰任何队伍的 prefix/suffix 字段。</p>
 *
 * <p><b>线程模型</b>：{@link #applyTo} / {@link #removeFor} / {@link #refreshAllOnline} 必须在
 * 调度线程执行（调用方一律经 {@code ServerFacade.runSync} 或周期定时器）；
 * 聊天着色由监听器在异步聊天线程只读 {@link RankService#currentGroup}（LP 在线缓存，零阻塞）。</p>
 */
public final class PlayerRankDisplayService {

    private static final Logger LOGGER = Logger.getLogger("OrzMC.PlayerRankDisplay");

    /** 本服务独占的队伍名前缀（orzmc-op / orzmc-<group>，最长为 orzmc-builder 13 字符，≤16 协议上限）。 */
    private static final String TEAM_PREFIX = "orzmc-";

    private static final long PERIODIC_REFRESH_DELAY_TICKS = 100;
    private static final long PERIODIC_REFRESH_PERIOD_TICKS = 1200;

    private final ServerFacade serverFacade;
    private final RankService rankService;
    private final Supplier<RankColorsConfig> configSupplier;

    public PlayerRankDisplayService(
            ServerFacade serverFacade, RankService rankService, Supplier<RankColorsConfig> configSupplier) {
        this.serverFacade = serverFacade;
        this.rankService = rankService;
        this.configSupplier = configSupplier;
    }

    /** 当前玩家显示色：feature 关闭返回 null（聊天监听据此跳过）；OP 优先。 */
    public NamedTextColor colorFor(Player player) {
        RankColorsConfig config = configSupplier.get();
        if (!config.enabled()) {
            return null;
        }
        if (player.isOp()) {
            return config.opColor();
        }
        return colorFor(config, rankService.currentGroup(player.getUniqueId()));
    }

    /** 等级组 → 颜色；未知组回退 GRAY（对应访客）。 */
    private static NamedTextColor colorFor(RankColorsConfig config, String group) {
        return config.colors().getOrDefault(group, NamedTextColor.GRAY);
    }

    /** 当前权限组（委托 {@link RankService}，永不 null）。 */
    public String currentGroupOf(UUID playerId) {
        return rankService.currentGroup(playerId);
    }

    /**
     * 重设某在线玩家的头顶名牌队伍 + Tab 名（幂等）。必须在调度线程调用。
     *
     * <p>关闭 {@code enabled} 或 {@code tab_enabled} 时 Tab 名均 {@code playerListName} 置空，
     * 交由 client 计分板队伍/vanilla 渲染（恢复原 team 前缀+真实名）；{@code tab_enabled}
     * 关闭时保留头顶/聊天着色。若 {@code nametag_enabled} 开着玩家仍在 orzmc 队伍，Tab 回退
     * 会沿用该队伍色——team 机制固有交互。</p>
     */
    public void applyTo(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        RankColorsConfig config = configSupplier.get();
        if (!config.enabled()) {
            removeFor(player);
            // 总开关关闭同样置空：恢复服务器原显示策略（client 走计分板队伍/vanilla 渲染，含原 team 前缀）
            player.playerListName(null);
            return;
        }
        boolean op = player.isOp();
        // OP 时等级组不参与颜色与队伍命名，跳过 LP 查询（微优化）
        String group = op ? null : currentGroupOf(player.getUniqueId());
        NamedTextColor color = op ? config.opColor() : colorFor(config, group);
        // Tab 名组件：tab_enabled 关闭时置空（恢复服务器原显示策略，保留原 team 前缀），仅头顶/聊天着色
        Component tabName = config.tabEnabled() ? coloredTabName(player, color) : null;
        if (!config.nametagEnabled()) {
            // 只关头顶名牌：不建/不碰队伍；Tab 按 tab_enabled 着色或还原（避让占用计分板队伍的插件时用）
            removeFor(player);
            player.playerListName(tabName);
            return;
        }
        Scoreboard board = mainScoreboard();
        if (board == null) {
            return;
        }
        String entry = player.getName();
        // 队伍按「显示层级」命名：OP 归 orzmc-op，非 OP 归 orzmc-<组>——避免 OP 与同组非 OP
        // 共享同一队伍（队伍颜色全队一致）导致普通玩家被 OP 金色串色
        String displayKey = op ? "op" : group;
        // 冲突感知：entry 已被非 orzmc-* 队伍占用（其它插件接管名牌）→ 让位，只做 Tab 着色
        Team existing = board.getEntryTeam(entry);
        if (existing != null && !existing.getName().startsWith(TEAM_PREFIX)) {
            LOGGER.fine("玩家 " + entry + " 头顶队伍已被其它插件接管(" + existing.getName() + ")，让位跳过头顶着色");
            player.playerListName(tabName);
            return;
        }
        if (existing != null) {
            existing.removeEntry(entry);
        }
        Team team = ensureTeam(board, displayKey, color);
        if (team == null) {
            // 队伍名超协议上限（自定义超长 track 组名）→ 跳过头顶着色，Tab 按开关着色/还原
            player.playerListName(tabName);
            return;
        }
        // 强制刷新：removeEntry+addEntry 重发队伍包，让客户端重绘头顶名（规避 Paper 已知刷新遗漏）
        team.removeEntry(entry);
        team.addEntry(entry);
        player.playerListName(tabName);
    }

    /** 从 orzmc-* 队伍移除玩家 entry（退出/禁用时）。必须在调度线程调用。 */
    public void removeFor(Player player) {
        if (player == null) {
            return;
        }
        Scoreboard board = mainScoreboard();
        if (board == null) {
            return;
        }
        Team team = board.getEntryTeam(player.getName());
        if (team != null && team.getName().startsWith(TEAM_PREFIX)) {
            team.removeEntry(player.getName());
        }
    }

    /** 按 UUID 刷新在线玩家显示（LP 等级变更实时刷新用）。 */
    public void refresh(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            applyTo(player);
        }
    }

    /** 重刷所有在线玩家（自愈 + /config reload 热生效）。 */
    public void refreshAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                applyTo(player);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "刷新玩家名颜色失败: " + player.getName(), e);
            }
        }
    }

    /** 周期自愈：延迟 100 tick 后每 1200 tick（≈60s）重刷一次。 */
    public void startPeriodicRefresh() {
        serverFacade.runTaskTimer(this::refreshAllOnline, PERIODIC_REFRESH_DELAY_TICKS, PERIODIC_REFRESH_PERIOD_TICKS);
    }

    /** Tab 名组件：displayName 纯文本（保留 EssentialsX /nick 昵称）强制 rank 色。 */
    private static Component coloredTabName(Player player, NamedTextColor color) {
        return Component.text(displayNameText(player)).color(color);
    }

    /** displayName 纯文本；为 null/空则回退真实名（Paper 默认 displayName 即真实名）。 */
    private static String displayNameText(Player player) {
        Component displayName = player.displayName();
        if (displayName == null) {
            return player.getName();
        }
        String text = PlainTextComponentSerializer.plainText().serialize(displayName);
        return text.isBlank() ? player.getName() : text;
    }

    /** 主计分板（null 守卫：测试环境可能无 ScoreboardManager）。 */
    private static Scoreboard mainScoreboard() {
        var manager = Bukkit.getScoreboardManager();
        return manager == null ? null : manager.getMainScoreboard();
    }

    /**
     * 取或建 orzmc-<displayKey> 队伍，并（总是）重设队伍颜色以热生效配置变更。
     *
     * <p>队伍名超协议上限（{@code orzmc-} + 自定义超长 track 组名 &gt; 16）时返回 null——
     * 调用方降级为「跳过头顶着色、只 Tab 着色」，避免 registerNewTeam 在调度线程抛异常。</p>
     */
    private Team ensureTeam(Scoreboard board, String displayKey, NamedTextColor color) {
        String teamName = TEAM_PREFIX + displayKey;
        if (teamName.length() > 16) {
            LOGGER.fine("队伍名超长(" + teamName + ")，跳过头顶着色，仅 Tab/聊天着色");
            return null;
        }
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        team.color(color);
        return team;
    }
}
