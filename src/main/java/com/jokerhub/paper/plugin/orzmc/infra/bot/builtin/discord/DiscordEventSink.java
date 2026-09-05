package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

/**
 * Discord 入站事件回调（builtin Discord adapter）。
 *
 * <p>网关客户端只把 op0 事件透传到此处（事件归一/线程调度归 Processor）——回调收到事件类型与原始网关帧
 * JSON（含 op/s/t/d，供 {@link DiscordInboundParser} 解析 MESSAGE_CREATE 等）。READY/RESUMED 等生命周期
 * 事件已由 {@link DiscordGatewayClient} 内部消费，不会回调。</p>
 *
 * <p><b>线程纪律（R12）</b>：回调在 WS 读线程触发，实现方<b>不得</b>直接触碰 Bukkit API——入站事件必须
 * 另行经 ServerScheduler.runSync 调度到服务器线程后再进命令层。</p>
 */
@FunctionalInterface
public interface DiscordEventSink {

    /**
     * 收到一条 op0 网关事件。
     *
     * @param type 事件类型（如 {@code MESSAGE_CREATE} / {@code GUILD_CREATE}）
     * @param rawFrame 原始网关帧 JSON（含 {@code op}/{@code s}/{@code t}/{@code d} 字段）
     */
    void onGatewayEvent(String type, String rawFrame);
}
