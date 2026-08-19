package com.jokerhub.paper.plugin.orzmc.features.review;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 审核处理策略：审核通过时对申请人执行的动作。
 *
 * <p>由 {@link ReviewType} 持有，新增审核流程只需实现本接口并在枚举注册。</p>
 *
 * <p><b>异步约束</b>：授权处理（LP promote 等）可能等待 LuckPerms 的异步 future，
 * 而 LP 的 future 完成回调调度到服务器同步调度线程（Folia global / Paper 主线程）执行——
 * 因此服务器调度线程绝不能同步等待授权结果。本接口返回 {@link CompletableFuture}，
 * 授权实现必须在自己管理的非服务器线程上执行 LP 操作（见
 * {@code LuckPermsPromoter.promoteAsync}），审核框架异步等待结果后再落状态。</p>
 */
@FunctionalInterface
public interface ReviewHandler {

    /**
     * 异步执行审核通过后的处理（如授予权限组）。
     *
     * @return 完成时给出授权结果：true=授权成功；false=授权失败（如目标已在链顶/LP 异常）
     *     ——调用方据结果决定最终申请状态（成功→APPROVED，失败→保持 PENDING），杜绝状态漂移
     */
    CompletableFuture<Boolean> onApproved(UUID applicantId);
}
