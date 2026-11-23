package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

/**
 * 网关运行时诊断快照（只读、不可变；issue #616）。
 *
 * <p>此前这些数字只出现在「静默超时」那一行 WARN 里——要判断「入站断流」还是「调度被拖住」必须翻日志，
 * 而且现场早已过去。快照把同一组计数暴露给 {@code /config im status}，管理员在故障**正在发生**时就能取到：</p>
 *
 * <ul>
 *   <li>{@code remoteAddress}：本条连接的对端 {@code host:port}。连续多次静默断流都命中同一地址时，
 *       问题指向「这条出网路径对该端点不友好」，而不是服务端随机故障；</li>
 *   <li>{@code inboundFrames} / {@code heartbeatSends}：本轮连接累计入站活性 / 已发心跳帧。已发 &gt; 0 而入站为 0
 *       = 出站正常、对端或链路不回（与 issue #616 的实测一致）；</li>
 *   <li>{@code silentMs}：距上次入站活性（含 WS ping/pong 控制帧）的毫秒数；</li>
 *   <li>{@code silenceLosses}：本段不稳定期的累计静默重连次数（连接真正收到过入站帧后清零）；</li>
 *   <li>{@code sameAddressSilenceStreak}：连续「同一远端地址」的静默断流次数（收到过帧也计入）——达到
 *       {@link ReconnectingGateway#SAME_ADDRESS_SILENCE_HINT} 后插件会给排查提示并放大退避；</li>
 *   <li>{@code reconnectAttempts}：连续重连尝试次数（连接稳定且收到过入站帧后清零）。</li>
 * </ul>
 *
 * @param state 状态机状态（{@code IDLE} / {@code CONNECTING} / {@code OPEN} / {@code RECONNECTING} / {@code FATAL}…）
 * @param remoteAddress 对端地址 {@code host:port}（未连接时为 {@code null}）
 * @param inboundFrames 本轮累计入站活性数（文本/二进制帧 + WS ping/pong）
 * @param heartbeatSends 本轮已发心跳帧数
 * @param silentMs 距上次入站活性的毫秒数（从未收到过则为本轮已连接时长）
 * @param silenceLosses 本段不稳定期累计静默重连次数
 * @param sameAddressSilenceStreak 连续同一远端地址静默断流次数
 * @param reconnectAttempts 连续重连尝试次数
 * @param lastProblem 最近一次问题描述（断开码/异常/致命原因；无则 {@code null}）
 */
public record GatewayDiagnostics(
        String state,
        String remoteAddress,
        long inboundFrames,
        long heartbeatSends,
        long silentMs,
        int silenceLosses,
        int sameAddressSilenceStreak,
        int reconnectAttempts,
        String lastProblem) {}
