package com.jokerhub.paper.plugin.orzmc.features.security;

/**
 * 玩家名规则的增删反馈构建（bot {@code $d} 与游戏内 {@code /blacklist} 共用，消除重复）。
 *
 * <p>两端各自负责参数形状差异（bot 拆原始串、游戏 Brigadier 分参）与空值守卫，
 * 本类统一「解析 → 合法/非法反馈 → 增/删与成功/未找到反馈」，避免两边实现漂移。</p>
 */
public final class PlayerNameRuleFeedback {

    private PlayerNameRuleFeedback() {}

    /** 反馈结果：{@code success} 为 false 表示参数非法或未找到（调用方应渲染为错误样式/模板）。 */
    public record Outcome(boolean success, String message) {}

    /**
     * 解析并执行玩家名规则增删，返回统一反馈消息。
     *
     * <p>{@code typeRaw} 为原始匹配类型词（如 {@code exact}），{@code valueRaw} 为规则值
     * （也是持久化/移除时的键值）。入口 trim 并做空值守卫，与 {@code AccessRuleService}
     * 的归一化口径一致。非法类型/非法正则返回失败反馈，不触碰服务。</p>
     */
    public static Outcome feedback(AccessRuleService svc, String typeRaw, String valueRaw, boolean remove) {
        if (valueRaw == null || valueRaw.isBlank()) {
            return new Outcome(false, "规则值不能为空");
        }
        String value = valueRaw.trim();
        PlayerNameRule.ParsedRule parsed = PlayerNameRule.parse(typeRaw, value);
        if (!parsed.valid()) {
            String msg = parsed.type() == null
                    ? "无效匹配类型: " + typeRaw + "（支持 exact/prefix/suffix/contains/glob/regex）"
                    : "无效的正则表达式: " + value;
            return new Outcome(false, msg);
        }
        String display = parsed.rule().display();
        if (remove) {
            return svc.removePlayerNameRule(parsed.type(), value)
                    ? new Outcome(true, "已移除玩家名规则: " + display)
                    : new Outcome(false, "未找到该玩家名规则: " + display);
        }
        boolean added = svc.addPlayerNameRule(parsed.type(), value);
        return added ? new Outcome(true, "已添加玩家名规则: " + display) : new Outcome(true, "玩家名规则已存在，未重复添加: " + display);
    }
}
