package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code $e} 命令输出组装：把「同步捕获行」与「日志时间窗行」合并为最终群消息文本。
 *
 * <p>规则：先同步行、后日志行（保序）；跳过空行与噪音行（命令回显
 * {@code issued server command}）；同步行与日志行可能重复（代理转发后仍写日志），
 * 用 {@link LinkedHashSet} 保序去重；超过 {@code maxLines} 截断并追加提示。
 */
public final class CommandOutputAssembler {

    private CommandOutputAssembler() {}

    /**
     * 组装输出文本。
     *
     * @param syncLines 同步捕获的命令输出行（可能为空）
     * @param windowLogLines 日志时间窗内新增的行（可能为空）
     * @param maxLines 最大行数，超过则截断
     * @return 组装后的多行文本；全部被过滤时返回空字符串
     */
    public static String assemble(List<String> syncLines, List<String> windowLogLines, int maxLines) {
        if (maxLines <= 0) {
            maxLines = 1;
        }
        List<String> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (syncLines != null) {
            for (String line : syncLines) {
                addFiltered(merged, seen, line);
            }
        }
        if (windowLogLines != null) {
            for (String line : windowLogLines) {
                addFiltered(merged, seen, line);
            }
        }
        if (merged.isEmpty()) {
            return "";
        }
        if (merged.size() <= maxLines) {
            return String.join("\n", merged);
        }
        List<String> head = new ArrayList<>(merged.subList(0, maxLines));
        head.add("…（输出过长，已截断，共 " + merged.size() + " 行）");
        return String.join("\n", head);
    }

    private static void addFiltered(List<String> out, Set<String> seen, String rawLine) {
        if (rawLine == null || rawLine.isBlank() || isNoise(rawLine)) {
            return;
        }
        String line = rawLine.trim();
        if (seen.add(line)) {
            out.add(line);
        }
    }

    /**
     * 噪音行判定：
     * <ul>
     *   <li>命令回显（{@code <sender> issued server command: /xxx}）</li>
     *   <li>玩家聊天行（{@code <Steve> 大家好}）——$e 日志窗口是全局兜底，活跃服务器
     *       上窗口内混入玩家聊天属已知限制，尽力过滤</li>
     * </ul>
     * 定位为「尽力而为」：无法做到命令级隔离（异步输出无法区分来源），
     * 不能保证窗口内 100% 无无关日志。
     */
    static boolean isNoise(String line) {
        if (line.contains("issued server command")) {
            return true;
        }
        return line.startsWith("<") && line.contains("> ");
    }
}
