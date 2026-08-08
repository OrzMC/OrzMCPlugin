package com.jokerhub.paper.plugin.orzmc.features.review;

import java.util.Map;
import java.util.UUID;

/**
 * 审核请求（值对象）：描述一次「玩家 X 提交了什么申请」。
 *
 * <p>「审核什么」由 {@link #data()} 结构化键值对明确表达，
 * 框架不感知具体业务（晋升/白名单/领地…），仅负责状态流转。</p>
 *
 * @param id          请求唯一标识（持久化用）
 * @param typeId      审核类型 id（{@link ReviewType#id()}）
 * @param applicantId 申请人 UUID
 * @param data        请求内容（键值对，如 target-group / reason）
 * @param status      当前状态
 * @param createdAt   提交时间戳（epoch millis）
 * @param reviewedAt  审核时间戳（未审核为 0）
 * @param reviewerName 审核人（群内昵称/游戏名，未审核为 null）
 */
public record ReviewRequest(
        String id,
        String typeId,
        UUID applicantId,
        Map<String, String> data,
        Status status,
        long createdAt,
        long reviewedAt,
        String reviewerName) {

    /** 审核状态流转：PENDING → APPROVED / REJECTED / CANCELLED。 */
    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    /** 生成审核完成后的新记录（状态 + 审核人 + 时间）。 */
    public ReviewRequest reviewed(Status newStatus, String reviewer) {
        return new ReviewRequest(
                id, typeId, applicantId, data, newStatus, createdAt, System.currentTimeMillis(), reviewer);
    }
}
