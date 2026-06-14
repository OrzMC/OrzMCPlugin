package com.jokerhub.paper.plugin.orzmc.infra.paging;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import java.util.ArrayList;
import java.util.List;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PaginatorTest extends ServiceTestBase {
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
}
