package com.jokerhub.paper.plugin.orzmc.features.rank;

import com.jokerhub.paper.plugin.orzmc.features.review.ReviewService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewType;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.Map;
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
    private final I18nService i18n;

    public RankCommandService(
            RankService service, ReviewService reviewService, OrzTextStyles styles, I18nService i18n) {
        this.service = service;
        this.reviewService = reviewService;
        this.styles = styles;
        this.i18n = i18n;
    }

    public sealed interface Result permits Result.Success, Result.Failure {
        record Success(Component message) implements Result {}

        record Failure(Component message) implements Result {}
    }

    /** /rank — 玩家查自己的权限组与进度。 */
    public Result status(Player player) {
        return statusOf(player.getUniqueId());
    }

    /** /rank &lt;玩家&gt; — admin 查指定玩家。
     *  状态/进度文案统一默认语言 R1（与审核业务一致）。 */
    public Result statusOf(UUID playerId) {
        String group = service.currentGroup(playerId);
        long minutes = service.playtimeMinutes(playerId);
        String displayName = RankService.groupDisplayName(group, i18n);

        // 状态描述按当前权限组动态化：时长/阈值行只在尚未完成自动晋升的组展示，
        // 「下一步」按组给对应引导（自动晋升 / 申请 / 无更高项 / 链顶）
        String timeLine;
        String nextLine;
        switch (group) {
            case "default" -> {
                long threshold = service.memberThresholdMinutes();
                String progress = minutes >= threshold
                        ? i18n.msg(i18n.langFor(), MessageKeys.RANK_PROGRESS_MET)
                        : i18n.msg(
                                i18n.langFor(),
                                MessageKeys.RANK_PROGRESS_LEFT,
                                Map.of("minutes", String.valueOf(threshold - minutes)));
                timeLine = i18n.msg(
                        i18n.langFor(),
                        MessageKeys.RANK_TIMELINE_WITH_PROGRESS,
                        Map.of(
                                "minutes", String.valueOf(minutes),
                                "threshold", String.valueOf(threshold),
                                "progress", progress));
                nextLine = i18n.msg(i18n.langFor(), MessageKeys.RANK_NEXT_AUTO);
            }
            case "member" -> {
                long threshold = service.memberThresholdMinutes();
                timeLine = i18n.msg(
                        i18n.langFor(),
                        MessageKeys.RANK_TIMELINE_DONE,
                        Map.of(
                                "minutes", String.valueOf(minutes),
                                "threshold", String.valueOf(threshold)));
                nextLine = i18n.msg(
                        i18n.langFor(), MessageKeys.RANK_NEXT_APPLY, Map.of("types", nextApplications(playerId)));
            }
            case "builder" -> {
                timeLine = i18n.msg(
                        i18n.langFor(), MessageKeys.RANK_TIMELINE_PLAIN, Map.of("minutes", String.valueOf(minutes)));
                nextLine = i18n.msg(
                        i18n.langFor(), MessageKeys.RANK_NEXT_APPLY, Map.of("types", nextApplications(playerId)));
            }
            case "admin" -> {
                timeLine = i18n.msg(
                        i18n.langFor(), MessageKeys.RANK_TIMELINE_PLAIN, Map.of("minutes", String.valueOf(minutes)));
                nextLine = i18n.msg(
                        i18n.langFor(),
                        MessageKeys.RANK_TOP_LEVEL,
                        Map.of("admin", i18n.msg(i18n.langFor(), MessageKeys.RANK_GROUP_ADMIN)));
            }
            default -> {
                timeLine = i18n.msg(
                        i18n.langFor(), MessageKeys.RANK_TIMELINE_PLAIN, Map.of("minutes", String.valueOf(minutes)));
                nextLine = i18n.msg(i18n.langFor(), MessageKeys.RANK_UNKNOWN_GROUP);
            }
        }

        String header =
                i18n.msg(i18n.langFor(), MessageKeys.RANK_HEADER_CURRENT, Map.of("name", displayName, "code", group));
        Component message = styles.info(header + "\n" + timeLine + "\n" + nextLine);
        return new Result.Success(message);
    }

    /** 反向生成「下一步可申请」：注册表中资格预检通过的类型。 */
    private String nextApplications(UUID playerId) {
        List<String> available = reviewService.registeredTypes().stream()
                .filter(t -> t.isEligible(playerId))
                .map(this::formatAvailableType)
                .collect(Collectors.toList());
        if (available.isEmpty()) {
            return i18n.msg(i18n.langFor(), MessageKeys.RANK_NO_APPLICABLE);
        }
        return String.join(i18n.msg(i18n.langFor(), MessageKeys.RANK_LIST_SEP), available);
    }

    private String formatAvailableType(ReviewType type) {
        return i18n.msg(
                i18n.langFor(),
                MessageKeys.RANK_TYPE_ENTRY,
                Map.of("name", i18n.msg(i18n.langFor(), "review.type." + type.id()), "key", type.commandKey()));
    }
}
