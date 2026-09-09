package com.jokerhub.paper.plugin.orzmc.features.review;

import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.Lang;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * 审核游戏内命令服务：/apply（玩家提交/查询/撤回）+ /review（管理员审核）。
 *
 * <p>薄封装，逻辑全部走 {@link ReviewService}；命令注册在 FeatureModule（Brigadier）。</p>
 */
public final class ReviewCommandService {

    private final ReviewService reviewService;
    private final OrzTextStyles styles;
    private final I18nService i18n;

    public ReviewCommandService(ReviewService reviewService, OrzTextStyles styles, I18nService i18n) {
        this.reviewService = reviewService;
        this.styles = styles;
        this.i18n = i18n;
    }

    public sealed interface Result permits Result.Success, Result.Failure {
        record Success(Component message) implements Result {}

        record Failure(Component message) implements Result {}
    }

    /** /apply — 列出可申请类型（注册表驱动 + 按当前玩家资格过滤，自动生成帮助）。 */
    public Result listTypes(Player player) {
        Lang lang = i18n.langFor(player);
        List<String> lines = reviewService.registeredTypes().stream()
                .filter(t -> t.isEligible(player.getUniqueId()))
                .map(t -> "· " + i18n.msg(lang, "review.type." + t.id()) + " — /apply " + t.commandKey() + " "
                        + i18n.msg(lang, MessageKeys.REVIEW_REASON_ARG))
                .collect(Collectors.toList());
        if (lines.isEmpty()) {
            return new Result.Failure(styles.error(i18n.msg(lang, MessageKeys.REVIEW_NO_TYPES)));
        }
        return new Result.Success(
                styles.info(i18n.msg(lang, MessageKeys.REVIEW_LIST_HEADER) + "\n" + String.join("\n", lines)));
    }

    /** /apply &lt;type&gt; [理由] — 提交申请。 */
    public Result apply(Player player, String typeKey, String rawArgs) {
        UUID id = player.getUniqueId();
        Lang lang = i18n.langFor(player);
        ReviewType type = reviewService.registeredTypes().stream()
                .filter(t -> t.commandKey().equalsIgnoreCase(typeKey))
                .findFirst()
                .orElse(null);
        if (type == null) {
            return new Result.Failure(
                    styles.error(i18n.msg(lang, MessageKeys.REVIEW_TYPE_UNKNOWN, Map.of("type", typeKey))));
        }
        Map<String, String> data = type.parseArgs(rawArgs);
        ReviewService.Result result = reviewService.submit(type, id, data);
        return result.success()
                ? new Result.Success(styles.success(result.message()))
                : new Result.Failure(styles.error(result.message()));
    }

    /** /apply status — 查看自己的申请及状态。 */
    public Result status(Player player) {
        UUID id = player.getUniqueId();
        Lang lang = i18n.langFor(player);
        List<ReviewRequest> requests = reviewService.listByApplicant(id);
        if (requests.isEmpty()) {
            return new Result.Success(styles.info(i18n.msg(lang, MessageKeys.REVIEW_NO_APPLICATIONS)));
        }
        StringBuilder sb = new StringBuilder(i18n.msg(lang, MessageKeys.REVIEW_MY_APPLICATIONS)).append('\n');
        for (ReviewRequest r : requests) {
            String typeName = reviewService
                    .typeById(r.typeId())
                    .map(ReviewType::displayName)
                    .orElse(r.typeId());
            sb.append("· ")
                    .append(typeName)
                    .append(" — ")
                    .append(statusText(lang, r))
                    .append("\n");
        }
        return new Result.Success(styles.info(sb.toString().trim()));
    }

    /** /apply cancel &lt;type&gt; — 撤回自己的待审申请。 */
    public Result cancel(Player player, String typeKey) {
        UUID id = player.getUniqueId();
        Lang lang = i18n.langFor(player);
        ReviewType type = reviewService.registeredTypes().stream()
                .filter(t -> t.commandKey().equalsIgnoreCase(typeKey))
                .findFirst()
                .orElse(null);
        if (type == null) {
            return new Result.Failure(
                    styles.error(i18n.msg(lang, MessageKeys.REVIEW_TYPE_UNKNOWN_BARE, Map.of("type", typeKey))));
        }
        ReviewService.Result result = reviewService.cancelForApplicant(type, id);
        return result.success()
                ? new Result.Success(styles.success(result.message()))
                : new Result.Failure(styles.error(result.message()));
    }

    /**
     * /review approve|reject &lt;name&gt; — 管理员审核（按玩家名定位待审）。
     *
     * <p>异步：审核通过时授权处理（LP 晋升）在非服务器线程执行，命令立即返回，
     * 结果在授权完成后回调（回同步调度线程）。</p>
     */
    public CompletableFuture<Result> review(Player admin, String playerName, boolean approved) {
        // 先尝试按玩家名定位该玩家待审（若该玩家唯一待审）；多类型待审时用类型前缀
        return reviewService
                .reviewByApplicantName(playerName, approved, admin.getName())
                .thenApply(result -> result.success()
                        ? new Result.Success(styles.success(result.message()))
                        : new Result.Failure(styles.error(result.message())));
    }

    private String statusText(Lang lang, ReviewRequest r) {
        String base =
                switch (r.status()) {
                    case PENDING -> i18n.msg(lang, MessageKeys.REVIEW_STATUS_PENDING);
                    case APPROVED -> i18n.msg(lang, MessageKeys.REVIEW_STATUS_APPROVED);
                    case REJECTED -> i18n.msg(lang, MessageKeys.REVIEW_STATUS_REJECTED);
                    case CANCELLED -> i18n.msg(lang, MessageKeys.REVIEW_STATUS_CANCELLED);
                };
        if (r.reviewerName() == null) {
            return base;
        }
        return base + i18n.msg(lang, MessageKeys.REVIEW_REVIEWER_SUFFIX, Map.of("reviewer", r.reviewerName()));
    }
}
