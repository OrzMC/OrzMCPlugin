package com.jokerhub.paper.plugin.orzmc.infra.player;

import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * 在线玩家列表格式化（单一事实源）。
 *
 * <p>群消息中所有「玩家行」统一走本类：`$l` 命令（BotCommandListFeedbackService）
 * 与上下线广播（PlayerEventService）都调用 {@link #list(Collection, UUID)}，
 * 保证格式一致（玩家名(op) 游戏模式 权限组），避免两处各自维护漏改。</p>
 *
 * <p>rankService 可 null（setter 注入；未注入时省略权限组，兼容测试/无 LP 场景）。</p>
 */
public final class OnlineListFormatter {

    private RankService rankService;

    public void setRankService(RankService rankService) {
        this.rankService = rankService;
    }

    /** 单行：玩家名(op) 游戏模式 权限组（rankService 未注入时省略权限组）。 */
    public String line(Player p) {
        String group =
                rankService == null ? null : RankService.groupDisplayName(rankService.currentGroup(p.getUniqueId()));
        return PlayerDisplayNames.format(p, group);
    }

    /** 多行列表（每行一个玩家，\n 分隔）。 */
    public String list(Collection<? extends Player> players) {
        return list(players, null);
    }

    /**
     * 多行列表，可排除某玩家（下线/踢出广播时排除当事人）。
     *
     * @param excludeId 要排除的玩家 UUID，null 不排除
     */
    public String list(Collection<? extends Player> players, UUID excludeId) {
        StringBuilder sb = new StringBuilder();
        for (Player p : players) {
            if (excludeId != null && p.getUniqueId().equals(excludeId)) {
                continue;
            }
            sb.append(line(p)).append('\n');
        }
        return sb.toString().trim();
    }
}
