package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin;

/**
 * builtin 单平台适配器契约（S7 聚合；后续飞书/Discord/Telegram 同构实现）。
 *
 * <p>职责：连接生命周期（上行网关 start/stop/重连）+ 出站投递（下行 REST，{@code send(target, text)}，
 * target 为平台前缀会话串，如 {@code qq:group:<openid>} / {@code qq:user:<openid>}）。入站经内部接
 * {@code BotInboundHandler}（线程调度等由适配器内部完成，驱动不感知）。</p>
 */
public interface BuiltinPlatform {

    /** 平台标识（如 {@code qq}；与 im_bindings sessions 键 / 健康 key 前缀同构）。 */
    String platform();

    /** 启动（幂等：已在运行则无操作；FATAL 后可重启）。 */
    void start();

    /** 停止并清理（R13：幂等，等待线程终止）。 */
    void stop();

    /** 尝试重连（断开/降级时由 /bot 或 reload 路径调用）。 */
    void reconnectIfNeeded();

    /**
     * 出站文本投递（尽力一次不重试 D7；失败由适配器记健康告警）。
     *
     * @param target 平台前缀 target（本适配器不认领的前缀应被忽略并日志）
     * @param text 已格式化文本
     */
    void send(String target, String text);
}
