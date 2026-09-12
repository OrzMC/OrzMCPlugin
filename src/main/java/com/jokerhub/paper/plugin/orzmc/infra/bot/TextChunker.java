package com.jokerhub.paper.plugin.orzmc.infra.bot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 出站长文本分段（D2）：把一条过长的通知/回复按平台单条上限切成多条，避免被平台整条拒绝
 * （QQ {@code 40054007 消息长度超限}、TG/Discord 400 等——整条失败等于通知丢失）。
 *
 * <p><b>为什么按平台分两种口径</b>：Telegram（4096 字符）与 Discord（2000 字符）官方按<b>字符</b>限；
 * QQ 官方未公开数字（只有错误码 40054007），社区实现按 <b>UTF-8 字节</b>保守截断，故提供
 * {@link #byChars} 与 {@link #byUtf8Bytes} 两种。</p>
 *
 * <p>切分点优先「自然边界」（换行 → 句末标点 → 空格），避免把句子/代码块拦腰截断；始终按
 * <b>码点/字符</b>推进，绝不切碎多字节字符或代理对。段数超过 {@code maxParts} 时截断（平台侧限制，
 * 例：QQ 被动回复每条源消息最多 5 次），并置 {@code truncated=true} 供调用方告警——宁可少发并告警，
 * 也不要让平台整条拒绝。</p>
 */
public final class TextChunker {

    private TextChunker() {}

    /**
     * 分段结果。
     *
     * @param parts 分段文本（{@code truncated=true} 时最后一段仍是完整前缀，不含省略标记）
     * @param truncated 是否因 {@code maxParts} 限制丢弃了尾部内容（调用方应告警/落日志）
     */
    public record Result(List<String> parts, boolean truncated) {}

    /** 按字符数分段（Telegram 4096 / Discord 2000 等官方按字符口径）。 */
    public static Result byChars(String text, int maxChars, int maxParts) {
        return chunk(text, maxChars, maxParts, false);
    }

    /** 按 UTF-8 字节数分段（QQ：官方未公开字符口径，社区按字节截断）。 */
    public static Result byUtf8Bytes(String text, int maxBytes, int maxParts) {
        return chunk(text, maxBytes, maxParts, true);
    }

    private static Result chunk(String text, int limit, int maxParts, boolean byteMode) {
        if (text == null || text.isEmpty()) {
            return new Result(List.of(), false);
        }
        int safeLimit = Math.max(1, limit);
        int safeMaxParts = maxParts <= 0 ? Integer.MAX_VALUE : maxParts;
        if (size(text, byteMode) <= safeLimit) {
            return new Result(List.of(text), false); // 常见路径：未超限
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentSize = 0;
        int softBreakSize = -1; // 最近一次自然边界的「当前段内」大小
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int cpChars = Character.charCount(cp);
            int cpSize = sizeOfCodePoint(cp, byteMode);
            if (currentSize + cpSize > safeLimit) {
                // 需要切分：优先在自然边界断（且不产生过短的尾段）
                int cutAt = (softBreakSize >= safeLimit / 2) ? softBreakSize : currentSize;
                String head = cut(current, cutAt, byteMode);
                parts.add(head);
                if (parts.size() >= safeMaxParts) {
                    // 已达平台可接受的最大段数：本轮剩余内容丢弃（调用方告警）
                    return new Result(parts, true);
                }
                String rest = cutTail(current, cutAt, byteMode);
                current.setLength(0);
                current.append(rest);
                currentSize = size(rest, byteMode);
                softBreakSize = -1;
                continue; // 重新尝试放入当前码点
            }
            current.appendCodePoint(cp);
            currentSize += cpSize;
            if (isSoftBreak(cp)) {
                softBreakSize = currentSize;
            }
            i += cpChars;
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return new Result(parts, false);
    }

    /** 自然边界：换行 / 中英文句末标点 / 分号 / 空格（切在这之后）。 */
    private static boolean isSoftBreak(int cp) {
        return cp == '\n'
                || cp == '。'
                || cp == '！'
                || cp == '？'
                || cp == '；'
                || cp == '!'
                || cp == '?'
                || cp == ';'
                || cp == '.'
                || cp == ' ';
    }

    private static int size(String s, boolean byteMode) {
        return byteMode ? s.getBytes(StandardCharsets.UTF_8).length : s.codePointCount(0, s.length());
    }

    private static int sizeOfCodePoint(int cp, boolean byteMode) {
        if (!byteMode) {
            return 1;
        }
        return new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
    }

    /** 取当前段前 {@code targetSize}（按口径计量）个码点。 */
    private static String cut(StringBuilder sb, int targetSize, boolean byteMode) {
        int consumed = 0;
        int i = 0;
        while (i < sb.length()) {
            int cp = sb.codePointAt(i);
            int cpSize = sizeOfCodePoint(cp, byteMode);
            if (consumed + cpSize > targetSize) {
                break;
            }
            consumed += cpSize;
            i += Character.charCount(cp);
        }
        return sb.substring(0, i);
    }

    /** 取当前段自 {@code targetSize} 之后（含边界字符）的剩余部分。 */
    private static String cutTail(StringBuilder sb, int targetSize, boolean byteMode) {
        String head = cut(sb, targetSize, byteMode);
        return sb.substring(head.length());
    }
}
