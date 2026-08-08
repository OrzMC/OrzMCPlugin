package com.jokerhub.paper.plugin.orzmc.features.review;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 审核类型（注册表项）：描述一种可申请的「申请→审核→处理」流程元数据。
 *
 * <p>类型元数据（id/展示名/命令键/参数解析/资格预检/列表摘要）由消费者模块
 * （如 rank）构建并注册到 {@link ReviewService}；通过后的处理策略
 * 由 {@link #handler()} 携带（也是消费者注入）。框架只做路由与状态流转，
 * 新增审核类型零框架改动。</p>
 */
public record ReviewType(
        String id,
        String displayName,
        String commandKey,
        Function<String, Map<String, String>> argsParser,
        Predicate<UUID> eligibility,
        Function<Map<String, String>, String> summary,
        ReviewHandler handler) {

    /** 解析 /apply 子命令后的原始参数字符串 → 请求 data。 */
    public Map<String, String> parseArgs(String rawArgs) {
        return argsParser.apply(rawArgs == null ? "" : rawArgs);
    }

    /** 资格预检：玩家是否满足申请条件。 */
    public boolean isEligible(UUID applicantId) {
        return eligibility.test(applicantId);
    }

    /** 列表/通知摘要：data → 一句话描述请求内容。 */
    public String summarize(Map<String, String> data) {
        return summary.apply(data);
    }
}
