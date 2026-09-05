package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

/**
 * QQ 入站事件回调（builtin QQ adapter）。
 *
 * <p>S4 只负责把 op0 事件透传到此处（事件归一/线程调度归 S6）——回调收到的是事件类型与原始网关帧 JSON
 * （含 op/s/t/d，便于后续解析 group_openid / author.member_role / author.bot 等）。</p>
 *
 * <p><b>线程纪律（R12）</b>：回调在 WS 读线程触发，实现方<b>不得</b>直接触碰 Bukkit API——入站事件必须
 * 另行经 {@code ServerFacade.runSync} / SafeScheduler 调度到服务器线程后再进命令层（S6 落地）。</p>
 */
@FunctionalInterface
public interface QqEventSink {

    /**
     * 收到一条 op0 网关事件（READY / RESUMED 等生命周期事件已由 {@link QqGatewayClient} 内部消费，不会回调）。
     *
     * @param type 事件类型（如 {@code GROUP_AT_MESSAGE_CREATE} / {@code GROUP_MESSAGE_CREATE} / {@code C2C_MESSAGE_CREATE}）
     * @param rawFrame 原始网关帧 JSON（含 {@code op}/{@code s}/{@code t}/{@code d} 字段）
     */
    void onGatewayEvent(String type, String rawFrame);
}
