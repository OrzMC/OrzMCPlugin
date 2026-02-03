package com.jokerhub.paper.plugin.orzmc.infra.paging;

import com.jokerhub.paper.plugin.orzmc.infra.scheduler.Schedulers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Paginator {
    private Paginator() {}

    public static void paginate(
            Consumer<String> callback, String header, List<String> lines, int delayTicks, Integer page) {
        ArrayList<String> chunks = buildChunks(lines);
        int total = chunks.size();
        if (total == 0) {
            callback.accept(header + "\n" + "(暂无白名单玩家)");
            return;
        }
        if (page != null) {
            int idx = Math.max(1, Math.min(page, total)) - 1;
            String pageHeader = header + "\n第" + (idx + 1) + "/" + total + "页";
            String body = chunks.get(idx);
            callback.accept(pageHeader + "\n" + body);
        } else {
            for (int i = 0; i < total; i++) {
                final int pageIndex = i;
                Schedulers.runLater(
                        () -> {
                            String pageHeader = header + "\n第" + (pageIndex + 1) + "/" + total + "页";
                            String body = chunks.get(pageIndex);
                            callback.accept(pageHeader + "\n" + body);
                        },
                        i * (delayTicks <= 0 ? 5L : delayTicks));
            }
        }
    }

    public static void paginatePages(
            PageConsumer callback, String header, List<String> lines, int delayTicks, Integer page) {
        ArrayList<String> chunks = buildChunks(lines);
        int total = chunks.size();
        if (total == 0) {
            callback.accept(1, 1, header, "(暂无白名单玩家)");
            return;
        }
        if (page != null) {
            int idx = Math.max(1, Math.min(page, total)) - 1;
            String body = chunks.get(idx);
            callback.accept(idx + 1, total, header, body);
        } else {
            for (int i = 0; i < total; i++) {
                final int pageIndex = i;
                Schedulers.runLater(
                        () -> {
                            String body = chunks.get(pageIndex);
                            callback.accept(pageIndex + 1, total, header, body);
                        },
                        i * (delayTicks <= 0 ? 5L : delayTicks));
            }
        }
    }

    @FunctionalInterface
    public interface PageConsumer {
        void accept(int page, int total, String header, String body);
    }

    private static ArrayList<String> buildChunks(List<String> lines) {
        ArrayList<String> chunks = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String s : lines) {
            builder.append(s).append('\n');
            count++;
            if (count >= 20) {
                chunks.add(builder.toString().trim());
                builder = new StringBuilder();
                count = 0;
            }
        }
        if (!builder.isEmpty()) {
            chunks.add(builder.toString().trim());
        }
        return chunks;
    }
}
