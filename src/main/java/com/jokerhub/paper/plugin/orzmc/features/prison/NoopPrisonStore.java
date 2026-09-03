package com.jokerhub.paper.plugin.orzmc.features.prison;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** LuckPerms 缺失时的降级实现：坐牢功能不可用（false / 失败），插件其余功能正常。 */
public final class NoopPrisonStore implements PrisonLpGateway {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isPrisoner(UUID playerId) {
        return false;
    }

    @Override
    public CompletableFuture<ImprisonOutcome> imprison(UUID playerId, String originalLocation) {
        return CompletableFuture.completedFuture(new ImprisonOutcome(false, null));
    }

    @Override
    public CompletableFuture<ReleaseOutcome> release(UUID playerId) {
        return CompletableFuture.completedFuture(new ReleaseOutcome(false, false, null, null));
    }
}
