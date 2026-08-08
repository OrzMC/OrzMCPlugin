package com.jokerhub.paper.plugin.orzmc.features.review;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public ReviewCommandService(ReviewService reviewService, OrzTextStyles styles) {
        this.reviewService = reviewService;
        this.styles = styles;
    }

    public sealed interface Result permits Result.Success, Result.Failure {
        record Success(Component message) implements Result {}

        record Failure(Component message) implements Result {}
    }

    /** /apply — 列出可申请类型（注册表驱动 + 按当前玩家资格过滤，自动生成帮助）。 */
    public Result listTypes(Player player) {
        List<String> lines = reviewService.registeredTypes().stream()
                .filter(t -> t.isEligible(player.getUniqueId()))
                .map(t -> "· " + t.displayName() + " — /apply " + t.commandKey() + " [理由]")
                .collect(Collectors.toList());
        if (lines.isEmpty()) {
            return new Result.Failure(styles.error("当前没有可申请的审核类型。"));
        }
        return new Result.Success(styles.info("可申请：\n" + String.join("\n", lines)));
    }

    /** /apply &lt;type&gt; [理由] — 提交申请。 */
    public Result apply(Player player, String typeKey, String rawArgs) {
        UUID id = player.getUniqueId();
        ReviewType type = reviewService.registeredTypes().stream()
                .filter(t -> t.commandKey().equalsIgnoreCase(typeKey))
                .findFirst()
                .orElse(null);
        if (type == null) {
            return new Result.Failure(styles.error("未知申请类型: " + typeKey + "（/apply 查看可申请项）"));
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
        List<ReviewRequest> requests = reviewService.listByApplicant(id);
        if (requests.isEmpty()) {
            return new Result.Success(styles.info("你还没有提交过申请。"));
        }
        StringBuilder sb = new StringBuilder("你的申请：\n");
        for (ReviewRequest r : requests) {
            String typeName = reviewService
                    .typeById(r.typeId())
                    .map(ReviewType::displayName)
                    .orElse(r.typeId());
            sb.append("· ").append(typeName).append(" — ").append(statusText(r)).append("\n");
        }
        return new Result.Success(styles.info(sb.toString().trim()));
    }

    /** /apply cancel &lt;type&gt; — 撤回自己的待审申请。 */
    public Result cancel(Player player, String typeKey) {
        UUID id = player.getUniqueId();
        ReviewType type = reviewService.registeredTypes().stream()
                .filter(t -> t.commandKey().equalsIgnoreCase(typeKey))
                .findFirst()
                .orElse(null);
        if (type == null) {
            return new Result.Failure(styles.error("未知申请类型: " + typeKey));
        }
        ReviewService.Result result = reviewService.cancelForApplicant(type, id);
        return result.success()
                ? new Result.Success(styles.success(result.message()))
                : new Result.Failure(styles.error(result.message()));
    }

    /** /review approve|reject &lt;name&gt; — 管理员审核（按玩家名定位待审）。 */
    public Result review(Player admin, String playerName, boolean approved) {
        // 先尝试按玩家名定位该玩家待审（若该玩家唯一待审）；多类型待审时用类型前缀
        ReviewService.Result result = reviewService.reviewByApplicantName(playerName, approved, admin.getName());
        return result.success()
                ? new Result.Success(styles.success(result.message()))
                : new Result.Failure(styles.error(result.message()));
    }

    private String statusText(ReviewRequest r) {
        return switch (r.status()) {
            case PENDING -> "⏳ 待审核";
            case APPROVED -> "✅ 已通过" + (r.reviewerName() == null ? "" : "（" + r.reviewerName() + "）");
            case REJECTED -> "❌ 已拒绝" + (r.reviewerName() == null ? "" : "（" + r.reviewerName() + "）");
            case CANCELLED -> "↩️ 已撤回";
        };
    }
}
