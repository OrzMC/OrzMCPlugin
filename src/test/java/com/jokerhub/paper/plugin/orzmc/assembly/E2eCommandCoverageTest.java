package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.features.botcommands.OrzUserCmd;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 命令面覆盖率门禁：保证「Bot 命令」这个面向用户的功能面，每个都有 L1.5 自举 E2E 断言。
 *
 * <p>为什么需要它：闭环只能发现「已测的东西坏了」，发现不了「新功能没被测」。
 * 新增一个 {@link OrzUserCmd} 或新增一条断言后没同步，本测试会直接红——把「补断言」
 * 从口头约定变成 CI 强制。</p>
 *
 * <p>断言来源：解析 {@code build.gradle.kts} 里的 {@code ConsoleCheck(..., "orzdebug $x", ...)}
 * （自举 E2E 的控制台断言表，与 CI 实际执行的完全同源）。解析失败会显式报错，不会静默放行。</p>
 *
 * <p>尚未覆盖的命令必须登记在 {@link #NOT_COVERED} 并写明原因；一旦补上断言，必须同步删除登记，
 * 否则 {@code staleExceptionsAreRemoved} 会失败（防止"假例外"长期滞留）。</p>
 */
class E2eCommandCoverageTest {

    /** 自举 E2E 断言表所在文件（仓库根；Gradle test 的工作目录即模块根）。 */
    private static final Path BUILD_SCRIPT = Path.of("build.gradle.kts");

    /**
     * 明确暂不覆盖的 Bot 命令 + 原因（新增条目必须说明为什么不覆盖、何时覆盖）。
     *
     * <p><b>当前为空</b>：11 个 Bot 命令全部进了 L1.5 自举门禁——`$b`/`$o` 两条长任务在
     * 隔离 run 目录的小世界上跑（各几秒），并额外断言备份 zip 真的落盘。保留本机制供将来
     * 出现确实无法自举的命令时使用。</p>
     */
    private static final Map<String, String> NOT_COVERED = Map.of();

    @Test
    void everyBotCommandHasSelfHostedAssertion() {
        Set<String> covered = coveredBotCommands();
        assertTrue(
                covered.size() >= 5,
                () -> "解析 build.gradle.kts 只找到 " + covered.size() + " 条 orzdebug 断言，" + "断言表结构可能变了（本测试的解析需要同步更新）");

        Set<String> missing = new TreeSet<>(allBotCommandLetters());
        missing.removeAll(covered);
        missing.removeAll(NOT_COVERED.keySet());
        assertTrue(missing.isEmpty(), () -> "以下 Bot 命令没有 L1.5 自举断言（新增命令请补 e2e 断言，或在 NOT_COVERED 登记原因）: " + missing);
    }

    @Test
    void staleExceptionsAreRemoved() {
        Set<String> stale = new TreeSet<>(NOT_COVERED.keySet());
        stale.retainAll(coveredBotCommands());
        assertTrue(stale.isEmpty(), () -> "以下命令已被 e2e 覆盖，请从 NOT_COVERED 移除登记: " + stale);
    }

    @Test
    void exceptionsReferToRealCommands() {
        Set<String> unknown = new TreeSet<>(NOT_COVERED.keySet());
        unknown.removeAll(allBotCommandLetters());
        assertTrue(unknown.isEmpty(), () -> "NOT_COVERED 里的条目不是真实 Bot 命令（改名/删除后未同步）: " + unknown);
    }

    private static Set<String> allBotCommandLetters() {
        return Arrays.stream(OrzUserCmd.values())
                .map(OrzUserCmd::cmdName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 从断言表提取已覆盖的 Bot 命令字母。
     *
     * <p>Kotlin 源码里写作 {@code "orzdebug \$h"}（Kotlin 模板转义），先把 {@code \$} 归一成 {@code $} 再匹配，
     * 避免正则里同时处理两种转义。</p>
     */
    private static Set<String> coveredBotCommands() {
        String script;
        try {
            script = Files.readString(BUILD_SCRIPT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读不到 " + BUILD_SCRIPT.toAbsolutePath() + "（测试需在模块根运行）", e);
        }
        Matcher matcher = Pattern.compile("orzdebug\\s+\\$([a-z])").matcher(script.replace("\\$", "$"));
        Set<String> covered = new TreeSet<>();
        while (matcher.find()) {
            covered.add(matcher.group(1));
        }
        return covered;
    }
}
