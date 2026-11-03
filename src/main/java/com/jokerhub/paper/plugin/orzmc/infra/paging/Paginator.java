package com.jokerhub.paper.plugin.orzmc.infra.paging;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.scheduler.Schedulers;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Paginator {
    private Paginator() {}

    /**
     * 分页发送（不限页数）。
     *
     * @see #paginatePages(ServerScheduler, PageConsumer, String, String, List, int, Integer, int, String)
     */
    public static void paginatePages(
            ServerScheduler server,
            PageConsumer callback,
            String header,
            String emptyBody,
            List<String> lines,
            int delayTicks,
            Integer page) {
        paginatePages(server, callback, header, emptyBody, lines, delayTicks, page, 0, null);
    }

    /**
     * 分页发送（无页码参数时最多自动发 {@code maxPages} 页）。
     *
     * <p><b>为什么要上限：</b>一次入站消息的全部页面都是「同一条源消息的回复」——QQ 被动回复配额只有
     * 群 5 / 单聊 4 条，超出的部分会降级为<b>主动消息</b>（群 1000 条/天频控）。白名单大服一条 `$w`
     * 打十几页会抽干主动配额，还可能静默丢页。因此无页码参数时只发前 {@code maxPages} 页，
     * 并在最后一页追加 {@code truncatedHint}（提示用 {@code $w <页码>} 看其余）。</p>
     *
     * @param maxPages 无页码参数时的自动发送页数上限（{@code <= 0} = 不限）
     * @param truncatedHint 被截断时追加在最后一页尾部的提示（{@code null}/空白 = 不追加）
     */
    public static void paginatePages(
            ServerScheduler server,
            PageConsumer callback,
            String header,
            String emptyBody,
            List<String> lines,
            int delayTicks,
            Integer page,
            int maxPages,
            String truncatedHint) {
        ArrayList<String> chunks = buildChunks(lines);
        int total = chunks.size();
        if (total == 0) {
            callback.accept(1, 1, header, emptyBody);
            return;
        }
        if (page != null) {
            int idx = Math.max(1, Math.min(page, total)) - 1;
            String body = chunks.get(idx);
            callback.accept(idx + 1, total, header, body);
        } else {
            int send = (maxPages > 0 && total > maxPages) ? maxPages : total;
            boolean truncated = send < total;
            String hint = (truncated && truncatedHint != null && !truncatedHint.isBlank()) ? truncatedHint : null;
            for (int i = 0; i < send; i++) {
                final int pageIndex = i;
                final boolean appendHint = hint != null && pageIndex == send - 1;
                Schedulers.runLater(
                        server,
                        () -> {
                            try {
                                String body = chunks.get(pageIndex) + (appendHint ? "\n" + hint : "");
                                callback.accept(pageIndex + 1, total, header, body);
                            } catch (Exception e) {
                                Logger.getGlobal().log(Level.SEVERE, "Paginator paginatePages 延迟任务异常", e);
                            }
                        },
                        Math.max(1L, (long) i * (delayTicks <= 0 ? 5L : delayTicks)));
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
