package com.jokerhub.paper.plugin.orzmc.features.rank;

import com.jokerhub.paper.plugin.orzmc.features.review.ReviewService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewType;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * 权限查询命令服务：/rank（玩家查自己）/rank &lt;玩家&gt;（admin 查指定玩家）。
 *
 * <p>返回：当前权限组 + 在线时长/晋升进度 + 下一步可申请项（由审核注册表反向生成）。
 * 申请/审核命令不再在此（见 {@code ReviewCommandService}）。</p>
 */
public final class RankCommandService {

    private final RankService service;
    private final ReviewService reviewService;
    private final OrzTextStyles styles;

    public RankCommandService(RankService service, ReviewService reviewService, OrzTextStyles styles) {
        this.service = service;
        this.reviewService = reviewService;
        this.styles = styles;
    }

    public sealed interface Result permits Result.Success, Result.Failure {
        record Success(Component message) implements Result {}

        record Failure(Component message) implements Result {}
    }

    /** /rank — 玩家查自己的权限组与进度。 */
    public Result status(Player player) {
        return statusOf(player.getUniqueId());
    }

    /** /rank &lt;玩家&gt; — admin 查指定玩家。 */
    public Result statusOf(UUID playerId) {
        String group = service.currentGroup(playerId);
        long minutes = service.playtimeMinutes(playerId);
        String displayName = RankService.groupDisplayName(group);

        // 状态描述按当前权限组动态化：时长/阈值行只在尚未完成自动晋升的组展示，
        // 「下一步」按组给对应引导（自动晋升 / 申请 / 无更高项 / 链顶）
        String timeLine;
        String nextLine;
        switch (group) {
            case "default" -> {
                long threshold = service.memberThresholdMinutes();
                String progress = minutes >= threshold ? "✅ 已达标（下次上线将自动晋升为成员）" : "还需 " + (threshold - minutes) + " 分钟";
                timeLine = "已在线时长：" + minutes + " 分钟 / 晋升成员阈值 " + threshold + " 分钟（" + progress + "）";
                nextLine = "下一步：在线时长达标后自动晋升为成员";
            }
            case "member" -> {
                long threshold = service.memberThresholdMinutes();
                timeLine = "已在线时长：" + minutes + " 分钟 / 晋升成员阈值 " + threshold + " 分钟（✅ 已达标）";
                nextLine = "下一步可申请：" + nextApplications(playerId);
            }
            case "builder" -> {
                timeLine = "已在线时长：" + minutes + " 分钟";
                nextLine = "下一步可申请：" + nextApplications(playerId);
            }
            case "admin" -> {
                timeLine = "已在线时长：" + minutes + " 分钟";
                nextLine = "已达最高等级（管理员）";
            }
            default -> {
                timeLine = "已在线时长：" + minutes + " 分钟";
                nextLine = "当前权限组未知，请联系管理员";
            }
        }

        Component message = styles.info("你的当前权限组：" + displayName + "（" + group + "）\n" + timeLine + "\n" + nextLine);
        return new Result.Success(message);
    }

    /** 反向生成「下一步可申请」：注册表中资格预检通过的类型。 */
    private String nextApplications(UUID playerId) {
        List<String> available = reviewService.registeredTypes().stream()
                .filter(t -> t.isEligible(playerId))
                .map(this::formatAvailableType)
                .collect(Collectors.toList());
        return available.isEmpty() ? "无（当前无可申请项）" : String.join("；", available);
    }

    private String formatAvailableType(ReviewType type) {
        return type.displayName() + "（/apply " + type.commandKey() + "）";
    }
}
