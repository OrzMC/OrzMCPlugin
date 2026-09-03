package com.jokerhub.paper.plugin.orzmc.features.prison;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/**
 * 坐牢命令服务：/prison &lt;玩家&gt; on|off（管理员执行坐牢/解除坐牢）。
 *
 * <p>玩家名 → UUID 解析委托给注入的解析器（装配层传 {@code rankService::resolvePlayerId}，
 * 与 /rank 命令一致），解析失败/离线未知玩家给友好反馈。LP 操作异步执行
 * （{@link PrisonService} 内部经 gateway 异步执行器），调用线程不阻塞。</p>
 */
public final class PrisonCommandService {

    private final PrisonService service;
    private final OrzTextStyles styles;
    private final Function<String, UUID> playerIdResolver;

    public PrisonCommandService(PrisonService service, OrzTextStyles styles, Function<String, UUID> playerIdResolver) {
        this.service = service;
        this.styles = styles;
        this.playerIdResolver = playerIdResolver;
    }

    /** 命令结果（成功/失败文案）。 */
    public sealed interface Result permits Result.Success, Result.Failure {
        record Success(Component message) implements Result {}

        record Failure(Component message) implements Result {}
    }

    /** /prison &lt;玩家&gt; on — 坐牢。 */
    public CompletableFuture<Result> imprison(String playerName) {
        UUID id = resolvePlayer(playerName);
        if (id == null) {
            return CompletableFuture.completedFuture(new Result.Failure(styles.error("找不到玩家: " + playerName)));
        }
        return service.imprison(id).thenApply(PrisonCommandService::toResult);
    }

    /** /prison &lt;玩家&gt; off — 解除坐牢。 */
    public CompletableFuture<Result> release(String playerName) {
        UUID id = resolvePlayer(playerName);
        if (id == null) {
            return CompletableFuture.completedFuture(new Result.Failure(styles.error("找不到玩家: " + playerName)));
        }
        return service.release(id).thenApply(PrisonCommandService::toResult);
    }

    private UUID resolvePlayer(String playerName) {
        return playerIdResolver == null ? null : playerIdResolver.apply(playerName);
    }

    private static Result toResult(PrisonService.Result result) {
        if (result instanceof PrisonService.Result.Success s) {
            return new Result.Success(s.message());
        }
        if (result instanceof PrisonService.Result.Failure f) {
            return new Result.Failure(f.message());
        }
        return new Result.Failure(Component.empty());
    }
}
