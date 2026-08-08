package com.jokerhub.paper.plugin.orzmc.features.review;

import java.util.UUID;

/**
 * 审核处理策略：审核通过时对申请人执行的动作。
 *
 * <p>由 {@link ReviewType} 持有，新增审核流程只需实现本接口并在枚举注册。</p>
 */
@FunctionalInterface
public interface ReviewHandler {

    /**
     * 执行审核通过后的处理（如授予权限组）。
     *
     * @return true=授权成功；false=授权失败（如目标已在链顶/LP 异常）——调用方应保持
     *     PENDING 状态并提示，避免「已通过但未生效」的不一致
     */
    boolean onApproved(UUID applicantId);
}
