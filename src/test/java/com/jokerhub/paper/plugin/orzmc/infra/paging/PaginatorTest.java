package com.jokerhub.paper.plugin.orzmc.infra.paging;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PaginatorTest extends ServiceTestBase {

    /** 记录 runLater 的 delay 值，用于断言 Folia 兼容性（delay ≥ 1） */
    private static final class RecordingScheduler implements ServerScheduler {
        final List<Long> delays = new ArrayList<>();

        @Override
        public void runSync(Runnable task) {
            task.run();
        }

        @Override
        public void runAsync(Runnable task) {
            task.run();
        }

        @Override
        public void runLater(Runnable task, long delayTicks) {
            delays.add(delayTicks);
            task.run();
        }
    }

    @Test
    public void testPaginatePagesRunsAllPages() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            lines.add("line-" + i);
        }
        List<String> pageBodies = new ArrayList<>();
        List<Integer> pageIndexes = new ArrayList<>();
        List<Integer> totals = new ArrayList<>();
        List<String> headers = new ArrayList<>();

        Paginator.paginatePages(
                new ImmediateScheduler(),
                (page, total, header, body) -> {
                    pageIndexes.add(page);
                    totals.add(total);
                    headers.add(header);
                    pageBodies.add(body);
                },
                "HEADER",
                lines,
                0,
                null);

        Assertions.assertEquals(2, pageBodies.size());
        Assertions.assertEquals(List.of(1, 2), pageIndexes);
        Assertions.assertEquals(List.of(2, 2), totals);
        Assertions.assertEquals(List.of("HEADER", "HEADER"), headers);
        Assertions.assertTrue(pageBodies.get(0).contains("line-0"));
        Assertions.assertTrue(pageBodies.get(0).contains("line-19"));
        Assertions.assertTrue(pageBodies.get(1).contains("line-20"));
        Assertions.assertTrue(pageBodies.get(1).contains("line-24"));
    }

    /** BUG-E2E-001 回归护栏：分页首页（i=0）delay 必须 ≥ 1（Folia runDelayed 拒绝 0） */
    @Test
    public void testPaginatePages_firstPageDelayIsAtLeastOne() {
        RecordingScheduler sched = new RecordingScheduler();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            lines.add("line-" + i);
        }
        Paginator.paginatePages(sched, (p, t, h, b) -> {}, "H", lines, 5, null);
        Assertions.assertEquals(2, sched.delays.size());
        // i=0 → delay ≥ 1；i=1 → delay = 1*5 = 5
        Assertions.assertTrue(sched.delays.get(0) >= 1, "首页 delay 必须 ≥ 1，实际 " + sched.delays.get(0));
        Assertions.assertEquals(5L, sched.delays.get(1));
    }

    /** 同护栏覆盖 paginate（老入口）：delayTicks=0 时首页 delay 也必须 ≥ 1 */
    @Test
    public void testPaginate_firstPageDelayIsAtLeastOne() {
        RecordingScheduler sched = new RecordingScheduler();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            lines.add("l" + i);
        }
        Paginator.paginate(sched, s -> {}, "H", lines, 0, null);
        Assertions.assertEquals(2, sched.delays.size());
        Assertions.assertTrue(sched.delays.get(0) >= 1, "首页 delay 必须 ≥ 1，实际 " + sched.delays.get(0));
        // delayTicks=0 → 按 5 兜底
        Assertions.assertEquals(5L, sched.delays.get(1));
    }

    @Test
    public void testPaginatePages_emptyList() {
        Paginator.paginatePages(
                new ImmediateScheduler(),
                (page, total, header, body) -> {
                    Assertions.assertEquals(1, page);
                    Assertions.assertEquals(1, total);
                    Assertions.assertEquals("H", header);
                    Assertions.assertEquals("(暂无白名单玩家)", body);
                },
                "H",
                List.of(),
                5,
                null);
    }

    @Test
    public void testPaginatePages_singlePageOneDelayTask() {
        // 单页时仍调度 1 次 runLater（i=0，delay ≥ 1）——源码行为：统一走延迟回调
        RecordingScheduler sched = new RecordingScheduler();
        Paginator.paginatePages(sched, (p, t, h, b) -> {}, "H", List.of("only"), 5, null);
        Assertions.assertEquals(1, sched.delays.size(), "单页应恰好 1 次延迟任务");
        Assertions.assertTrue(sched.delays.get(0) >= 1, "单页延迟也必须 ≥ 1，实际 " + sched.delays.get(0));
    }

    @Test
    public void testPaginatePages_pageClamping() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 45; i++) { // 3 页
            lines.add("line-" + i);
        }
        // page=0 → 钳到第 1 页
        Paginator.paginatePages(
                new ImmediateScheduler(),
                (page, total, header, body) -> Assertions.assertEquals(1, page),
                "H",
                lines,
                5,
                0);
        // page=999 → 钳到最后一页（第 3 页）
        Paginator.paginatePages(
                new ImmediateScheduler(),
                (page, total, header, body) -> Assertions.assertEquals(3, page),
                "H",
                lines,
                5,
                999);
        // page=-5 → 钳到第 1 页
        Paginator.paginatePages(
                new ImmediateScheduler(),
                (page, total, header, body) -> Assertions.assertEquals(1, page),
                "H",
                lines,
                5,
                -5);
    }

    @Test
    public void testPaginatePages_negativeDelayUsesFallback() {
        // delayTicks ≤ 0 → 每页间隔用 5 兜底（且首页 ≥ 1）
        RecordingScheduler sched = new RecordingScheduler();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 41; i++) { // 3 页
            lines.add("l" + i);
        }
        Paginator.paginatePages(sched, (p, t, h, b) -> {}, "H", lines, -3, null);
        Assertions.assertEquals(3, sched.delays.size());
        Assertions.assertTrue(sched.delays.get(0) >= 1);
        Assertions.assertEquals(5L, sched.delays.get(1));
        Assertions.assertEquals(10L, sched.delays.get(2));
    }

    private static final class ImmediateScheduler implements ServerScheduler {
        @Override
        public void runSync(Runnable task) {
            task.run();
        }

        @Override
        public void runAsync(Runnable task) {
            task.run();
        }

        @Override
        public void runLater(Runnable task, long delayTicks) {
            task.run();
        }
    }
}
