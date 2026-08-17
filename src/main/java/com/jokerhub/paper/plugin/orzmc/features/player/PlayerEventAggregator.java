package com.jokerhub.paper.plugin.orzmc.features.player;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.PlayerNotifyConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.templates.CoordFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 玩家上下线通知的窗口聚合器（无丢消息）。
 *
 * <p>JOIN/QUIT/KICK 事件在聚合窗口内累积，窗口尾部统一冲刷为一条消息：
 * <ul>
 *   <li>窗口内仅 1 条事件 → 复用原单条模板（player_join / player_quit / player_kick），
 *       延迟一个窗口发出（保持最多 1 条/窗口的速率上界）；</li>
 *   <li>窗口内多条事件 → 渲染聚合摘要 {@code player_digest}，按状态精确计数，
 *       玩家名超长仅显示截断（追加 {@code +等N人}），计数不受影响。</li>
 * </ul>
 * 没有任何静默丢弃路径：限流完全通过窗口合并实现，每条事件要么作为唯一事件单条渲染，
 * 要么进入摘要被精确计数。渲染失败（模板/通知异常）时保留批次并在下一窗口重试
 * （连续失败上限 {@link #MAX_RENDER_RETRIES} 后放弃并告警，避免无限重试/批次无限增长）。</p>
 *
 * <p>事件（Bukkit 事件监听器）与冲刷（{@code runLater} 同步任务）都在主线程，
 * 无并发竞态。配置每次 {@link #enqueue} 时实时读取，热重载立即生效；冲刷时也会
 * 按最新配置过滤（窗口内被关闭的类型不再发送，属于配置性忽略而非运行时丢消息）。</p>
 *
 * <p>当事人属性（玩家名/显示行/坐标/UUID）在 {@link #enqueue} 时快照：冲刷可能发生在
 * 当事人已离线之后，避免读取离线玩家的属性（null 坐标 / null 显示名）。</p>
 *
 * <p>真实 Paper 中一次成功的踢出会同时触发 {@code PlayerKickEvent} 与 {@code PlayerQuitEvent}
 * （同一离开被上报两次）。聚合器在批次内按玩家 UUID 去重：窗口内同一玩家已有 KICK 时，
 * 随后的 QUIT 折叠为「被踢」单次计数（保留更具体的语义），避免摘要双计。
 * （MockBukkit 的 {@code kick()} 不触发 QuitEvent，该去重对真实服生效、对测试无副作用。）</p>
 *
 * <p>插件禁用/重载时 Bukkit 会取消插件待执行任务，窗口尾部调度可能来不及运行，批次将
 * 静默丢弃。{@link #flushPending()} 提供同步冲刷入口，由组合根在 {@code shutdownAll} 时
 * 调用，保证最后一个窗口的事件在卸载前交付（与「无静默丢弃」约束一致）。</p>
 *
 * <p>已知取舍（均为极低频或管理员显式配置变更的边界场景，不影响正常路径的精确计数）：
 * <ul>
 *   <li>渲染失败重试时，后续窗口的新事件并入重试批次（恢复后一并冲刷，见 {@link #MAX_RENDER_RETRIES}）；</li>
 *   <li>摘要的在线数/在线列表取冲刷时刻实时值，与窗口内被关闭（过滤）的类型可能不严格对齐；</li>
 *   <li>入队时被配置关闭的类型直接忽略（配置性丢弃，非运行时丢消息）。</li>
 * </ul></p>
 */
public final class PlayerEventAggregator {

    /** Bukkit 调度 tick 时长（毫秒）。 */
    private static final long TICKS_PER_MS = 50L;

    /** 单批次渲染连续失败的最大重试次数；达到后放弃并记录严重告警。 */
    private static final int MAX_RENDER_RETRIES = 3;

    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final Notifier notifier;
    private final OnlineListFormatter listFormatter;

    /** 当前聚合窗口批次（仅主线程访问）；null 表示无待冲刷批次。 */
    private PendingBatch batch;

    public PlayerEventAggregator(
            ServerFacade server, TypedConfigProvider configs, Notifier notifier, OnlineListFormatter listFormatter) {
        this.server = server;
        this.configs = configs;
        this.notifier = notifier;
        this.listFormatter = listFormatter;
    }

    /**
     * 收纳入队一条上下线事件。
     *
     * @param player 事件当事人
     * @param state  事件类型
     */
    public void enqueue(Player player, PlayerEventService.PlayerState state) {
        PlayerNotifyConfig cfg = configs.playerNotify();
        if (!enabled(cfg, state)) {
            // 管理员显式关闭该类型通知：配置性忽略，不属于运行时丢消息
            return;
        }
        // 入队时快照：冲刷可能发生在当事人已离线之后，避免读离线玩家属性（null 坐标/显示名）
        PendingEvent event = new PendingEvent(
                player.getUniqueId(), player.getName(), listFormatter.line(player), player.getLocation());
        PendingBatch current = batch;
        if (current == null) {
            current = new PendingBatch();
            long ticks = windowTicks(cfg);
            // 尾部调度成功后才入表：中途抛异常不留孤儿批次（该批次永远不会被冲刷）
            server.runLater(this::flushTail, ticks);
            batch = current;
        }
        current.add(event, state);
    }

    private static boolean enabled(PlayerNotifyConfig cfg, PlayerEventService.PlayerState state) {
        return switch (state) {
            case JOIN -> cfg.enabledJoin();
            case QUIT -> cfg.enabledQuit();
            case KICK -> cfg.enabledKick();
        };
    }

    private static long windowTicks(PlayerNotifyConfig cfg) {
        return Math.max(1, cfg.windowMs() / TICKS_PER_MS);
    }

    /**
     * 立即冲刷当前批次（同步，不走调度器）。
     *
     * <p>插件禁用/重载时由组合根调用：Bukkit 会取消插件待执行任务，窗口尾部调度可能来不及
     * 运行，此入口保证最后一个窗口的事件在卸载前交付。禁用场景下渲染失败不再重排，仅告警。</p>
     */
    public void flushPending() {
        flushAndRender(false);
    }

    /** 调度器回调：窗口尾部冲刷；渲染失败时保留批次重试（有界）。 */
    private void flushTail() {
        flushAndRender(true);
    }

    /** 窗口尾部冲刷：按最新配置过滤被关闭的类型；单发走原模板，多发走聚合摘要。 */
    private void flushAndRender(boolean retryOnFailure) {
        PendingBatch current = batch;
        batch = null;
        if (current == null || current.total() == 0) {
            return;
        }
        // 冲刷时按最新配置过滤：窗口内被关闭的类型不再发送（配置性忽略，非运行时丢消息）
        PlayerNotifyConfig cfg = configs.playerNotify();
        if (!cfg.enabledJoin()) {
            current.joins.clear();
        }
        if (!cfg.enabledQuit()) {
            current.quits.clear();
        }
        if (!cfg.enabledKick()) {
            current.kicks.clear();
        }
        if (current.total() == 0) {
            return;
        }
        try {
            if (current.total() == 1) {
                renderSingle(current.singleEvent(), current.singleState());
            } else {
                renderDigest(current);
            }
            // 渲染成功：批次已交付，无需保留
        } catch (RuntimeException e) {
            handleRenderFailure(current, e, retryOnFailure);
        }
    }

    /**
     * 渲染失败处理：保留批次并重试，避免整窗事件静默丢弃。
     *
     * <p>连续失败达到 {@link #MAX_RENDER_RETRIES} 后放弃（有界），记录严重告警并清空批次，
     * 避免模板永久损坏时无限重试与批次无限增长。禁用冲刷（{@code retryOnFailure=false}）
     * 失败仅告警不重排——插件卸载在即，重排的任务不会执行。</p>
     */
    private void handleRenderFailure(PendingBatch current, RuntimeException e, boolean retryOnFailure) {
        if (!retryOnFailure || current.renderRetries() >= MAX_RENDER_RETRIES) {
            server.logger()
                    .severe("上下线通知连续渲染失败 " + (current.renderRetries() + 1) + " 次，该窗口 " + current.total() + " 条事件无法发送: "
                            + e);
            return; // batch 已置 null：有界放弃（禁用冲刷仅告警，不重排）
        }
        current.retry();
        batch = current; // 恢复批次，下次冲刷重试
        server.logger().warning("上下线通知渲染失败，保留批次于下一窗口重试（第 " + current.renderRetries() + " 次）: " + e);
        server.runLater(this::flushTail, windowTicks(configs.playerNotify()));
    }

    /** 单发渲染：复用原单条模板。在线状态以冲刷时刻为准（当事人已离开/已加入），无需修正。 */
    private void renderSingle(PendingEvent event, PlayerEventService.PlayerState state) {
        ArrayList<Player> onlinePlayers = onlinePlayers();
        Map<String, String> vars = new HashMap<>();
        if (event.location != null) {
            TemplateOptions opt = configs.templateOptions();
            vars.putAll(CoordFormatter.format(event.location, opt));
        }
        vars.put(
                "world",
                event.location != null && event.location.getWorld() != null
                        ? event.location.getWorld().getName()
                        : "unknown");
        vars.put("name", event.displayLine);
        vars.put("online_count", String.valueOf(onlinePlayers.size()));
        vars.put("max_count", String.valueOf(server.server().getMaxPlayers()));
        vars.put("online_list", listFormatter.list(onlinePlayers));
        String eventKey =
                switch (state) {
                    case JOIN -> TemplateKeys.PLAYER_JOIN;
                    case QUIT -> TemplateKeys.PLAYER_QUIT;
                    case KICK -> TemplateKeys.PLAYER_KICK;
                };
        MessageEnvelope envelope = configs.renderEvent(eventKey, vars);
        notifier.event(eventKey, envelope);
        server.logger().info(envelope.message());
    }

    /** 多发渲染：按状态精确计数，玩家名显示截断（计数不受影响），可选附带在线列表。 */
    private void renderDigest(PendingBatch current) {
        PlayerNotifyConfig cfg = configs.playerNotify();
        String joinSummary = buildSummary(current.joins, "🟢", "上线", "+", cfg.maxListItems());
        String quitSummary = buildSummary(current.quits, "🔴", "下线", "-", cfg.maxListItems());
        String kickSummary = buildSummary(current.kicks, "⛔", "被踢", "-", cfg.maxListItems());
        ArrayList<Player> onlinePlayers = onlinePlayers();
        Map<String, String> vars = new HashMap<>();
        vars.put("join_summary", joinSummary);
        vars.put("quit_summary", quitSummary);
        vars.put("kick_summary", kickSummary);
        vars.put("online_count", String.valueOf(onlinePlayers.size()));
        vars.put("max_count", String.valueOf(server.server().getMaxPlayers()));
        vars.put("online_list", cfg.includeOnlineList() ? "\n" + listFormatter.list(onlinePlayers) : "");
        MessageEnvelope envelope = configs.renderEvent(TemplateKeys.PLAYER_DIGEST, vars);
        notifier.event(TemplateKeys.PLAYER_DIGEST, envelope);
        server.logger().info(envelope.message());
    }

    /** 单状态摘要：如 "🟢 +5 上线：A、B、C、D、E\n"；无事件返回空串。 */
    private static String buildSummary(
            List<PendingEvent> events, String marker, String action, String sign, int maxListItems) {
        if (events.isEmpty()) {
            return "";
        }
        return marker + " " + sign + events.size() + " " + action + "：" + joinNames(events, maxListItems) + "\n";
    }

    /** 玩家名列表（顿号分隔），超长仅截断显示并追加 "、等N人"（计数不受影响）。 */
    private static String joinNames(List<PendingEvent> events, int maxListItems) {
        int shown = Math.min(maxListItems, events.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(events.get(i).name);
        }
        int hidden = events.size() - shown;
        if (hidden > 0) {
            sb.append("、等").append(hidden).append("人");
        }
        return sb.toString();
    }

    private ArrayList<Player> onlinePlayers() {
        ArrayList<Player> result = new ArrayList<>();
        Object[] objects = server.server().getOnlinePlayers().toArray();
        for (Object obj : objects) {
            if (obj instanceof Player p) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 一条聚合事件：入队时的玩家快照。
     *
     * <p>冲刷发生在窗口尾部（当事人可能已离线），因此玩家 UUID/名/显示行/坐标均在入队时捕获，
     * 避免读取离线玩家属性（null 坐标 / null 显示名导致渲染异常或字段丢失）。</p>
     */
    private static final class PendingEvent {
        /** 玩家 UUID（KICK→QUIT 去重键）。 */
        final UUID playerId;
        /** 原始玩家名（摘要列表显示）。 */
        final String name;
        /** 显示行（玩家名(op) 游戏模式 权限组），入队时计算。 */
        final String displayLine;
        /** 入队时坐标快照；可能为 null（如当事人已离线但 Location 不可用）。 */
        final Location location;

        PendingEvent(UUID playerId, String name, String displayLine, Location location) {
            this.playerId = playerId;
            this.name = name;
            this.displayLine = displayLine;
            this.location = location;
        }
    }

    /** 一个聚合窗口内的待发批次（仅主线程访问）。 */
    private static final class PendingBatch {
        private final List<PendingEvent> joins = new ArrayList<>();
        private final List<PendingEvent> quits = new ArrayList<>();
        private final List<PendingEvent> kicks = new ArrayList<>();
        /** 本批次连续渲染失败次数（有界重试用）。 */
        private int renderRetries;

        void add(PendingEvent event, PlayerEventService.PlayerState state) {
            if (state == PlayerEventService.PlayerState.QUIT && hasKick(event.playerId)) {
                // 真实 Paper 中一次成功的踢出会同时触发 PlayerKickEvent 与 PlayerQuitEvent，
                // 同一离开被上报两次。窗口内同 playerId 已有 KICK → 该 QUIT 是踢出的跟随事件，
                // 折叠只保留更具体的「被踢」，避免摘要双计。
                // （极端场景「被踢→3 秒内重连→再次退出」可能被误折叠，概率极低且计数误差有界 ≤1。）
                return;
            }
            switch (state) {
                case JOIN -> joins.add(event);
                case QUIT -> quits.add(event);
                case KICK -> kicks.add(event);
            }
        }

        /** 本批次内该玩家是否已有 KICK（用于折叠其随后的 QUIT）。 */
        private boolean hasKick(UUID playerId) {
            if (playerId == null) {
                return false; // 防御性短路：理论真实玩家必有 UUID，null 不折叠
            }
            for (PendingEvent e : kicks) {
                if (playerId.equals(e.playerId)) {
                    return true;
                }
            }
            return false;
        }

        int total() {
            return joins.size() + quits.size() + kicks.size();
        }

        /** 单事件当事人；仅在 {@code total() == 1} 时调用。 */
        PendingEvent singleEvent() {
            if (!joins.isEmpty()) {
                return joins.get(0);
            }
            if (!quits.isEmpty()) {
                return quits.get(0);
            }
            return kicks.get(0);
        }

        /** 单事件类型；仅在 {@code total() == 1} 时调用。 */
        PlayerEventService.PlayerState singleState() {
            if (!joins.isEmpty()) {
                return PlayerEventService.PlayerState.JOIN;
            }
            if (!quits.isEmpty()) {
                return PlayerEventService.PlayerState.QUIT;
            }
            return PlayerEventService.PlayerState.KICK;
        }

        int renderRetries() {
            return renderRetries;
        }

        void retry() {
            renderRetries++;
        }
    }
}
