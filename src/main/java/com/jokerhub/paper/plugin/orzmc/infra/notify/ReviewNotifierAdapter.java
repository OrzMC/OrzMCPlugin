package com.jokerhub.paper.plugin.orzmc.infra.notify;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 审核通知端口实现：适配现有 {@link Notifier} + {@link TypedConfigProvider} 模板渲染。
 *
 * <ul>
 *   <li>游戏内消息：玩家在线即发（离线忽略，由群通知兜底）</li>
 *   <li>群推送：走 templates.yml 模板（review_submitted / review_cancelled / review_approved / review_rejected）</li>
 * </ul>
 */
public final class ReviewNotifierAdapter implements ReviewNotifier {

    private final TypedConfigProvider configs;
    private final Notifier notifier;

    public ReviewNotifierAdapter(TypedConfigProvider configs, Notifier notifier) {
        this.configs = configs;
        this.notifier = notifier;
    }

    @Override
    public void gameMessage(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(Component.text(message));
        }
    }

    @Override
    public void groupEvent(String templateKey, Map<String, String> vars) {
        // 用 renderTemplate（按配置键直接读取），不走 renderEvent 白名单事件路由
        String fallback =
                switch (templateKey) {
                    case TemplateKeys.REVIEW_SUBMITTED -> "📋 [新申请] {player}：{summary}（$v l 查看）";
                    case TemplateKeys.REVIEW_CANCELLED -> "↩️ {player} 撤回了申请：{summary}";
                    case TemplateKeys.REVIEW_APPROVED -> "✅ {player} 的申请已通过（审核人：{reviewer}）：{summary}";
                    case TemplateKeys.REVIEW_REJECTED -> "❌ {player} 的申请被拒（审核人：{reviewer}）：{summary}";
                    case TemplateKeys.RANK_PROMOTED -> "🎉 {player} 权限已升级为「{group}」";
                    case TemplateKeys.RANK_DEMOTED -> "⬇️ {player} 权限已被降级为「{group}」";
                    default -> "{message}";
                };
        MessageEnvelope env = configs.renderTemplate(templateKey, vars, fallback);
        notifier.event(templateKey, env);
    }
}
