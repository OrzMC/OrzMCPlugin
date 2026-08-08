package com.jokerhub.paper.plugin.orzmc.features.review;

import java.util.Map;
import java.util.UUID;

/**
 * 审核通知端口：审核框架对外发出通知的唯一出口。
 *
 * <p>宿主侧实现（适配现有 {@code Notifier} / {@code TypedConfigProvider}）：
 * <ul>
 *   <li>{@link #gameMessage} — 游戏内消息（玩家在线即发，离线忽略）</li>
 *   <li>{@link #groupEvent} — 群推送（走模板渲染，模板键见 TemplateKeys.REVIEW_*）</li>
 * </ul>
 * 测试中可用捕获实现断言通知内容。</p>
 */
public interface ReviewNotifier {

    /** 给玩家发游戏内消息（在线才发）。 */
    void gameMessage(UUID playerId, String message);

    /** 推送群事件通知（模板键 + 渲染变量）。 */
    void groupEvent(String templateKey, Map<String, String> vars);
}
