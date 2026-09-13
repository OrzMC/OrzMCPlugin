package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * 指南书颜色解析：统一「命名色」与「CSS hex」两种口径（与 {@code rank_colors} 一致）。
 *
 * <p>接受 {@code red} / {@code AQUA} 等 16 个命名色，以及 {@code #RGB} / {@code #RRGGBB}；
 * 空值与非法值一律返回 {@code null}（调用方回退默认色并告警），<b>不抛异常</b>。</p>
 *
 * <p>注：adventure 的 {@code TextColor.fromCSSHexString} 在不同版本对非法输入的行为不一致
 * （抛异常 / 返回 null），故此处显式校验 {@code #} 前缀与长度，避免依赖其实现细节。</p>
 */
public final class GuideColorParser {

    private GuideColorParser() {}

    /** 解析颜色；空值/非法值返回 {@code null}。 */
    public static TextColor parse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        int length = value.length();
        if (!value.startsWith("#") || (length != 4 && length != 7 && length != 9)) {
            return null;
        }
        return TextColor.fromCSSHexString(value);
    }

    /** 解析是否成功（供配置健康校验使用）。 */
    public static boolean isValid(String raw) {
        return parse(raw) != null;
    }
}
