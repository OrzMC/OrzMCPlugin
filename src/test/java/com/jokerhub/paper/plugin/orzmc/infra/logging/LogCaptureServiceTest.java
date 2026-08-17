package com.jokerhub.paper.plugin.orzmc.infra.logging;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class LogCaptureServiceTest {

    @Test
    void capture_singleLine_watermarkAndDrain() {
        LogCaptureService service = new LogCaptureService(10);
        long watermark = service.watermark();
        assertEquals(0, watermark);

        service.capture("hello");

        assertEquals(1, service.watermark());
        assertEquals(List.of("hello"), service.drainSince(watermark));
    }

    @Test
    void capture_multilineText_splitsIntoLines() {
        LogCaptureService service = new LogCaptureService(10);
        long watermark = service.watermark();

        service.capture("line1\nline2\nline3");

        assertEquals(List.of("line1", "line2", "line3"), service.drainSince(watermark));
    }

    @Test
    void capture_nullAndBlankLines_ignored() {
        LogCaptureService service = new LogCaptureService(10);
        long watermark = service.watermark();

        service.capture(null);
        service.capture("");
        service.capture("   ");
        service.capture("\n\n");

        assertEquals(List.of(), service.drainSince(watermark));
        assertEquals(0, service.watermark());
    }

    @Test
    void capture_crlfNormalized() {
        LogCaptureService service = new LogCaptureService(10);
        long watermark = service.watermark();

        service.capture("a\r\nb\rc");

        assertEquals(List.of("a", "b", "c"), service.drainSince(watermark));
    }

    @Test
    void capture_stripsAnsiColorCodes() {
        LogCaptureService service = new LogCaptureService(10);
        long watermark = service.watermark();

        service.capture("\u001B[33m黄色输出\u001B[0m plain");

        assertEquals(List.of("黄色输出 plain"), service.drainSince(watermark));
    }

    @Test
    void drainSince_onlyReturnsNewerThanWatermark() {
        LogCaptureService service = new LogCaptureService(10);
        service.capture("before");

        long watermark = service.watermark();
        service.capture("after1");
        service.capture("after2");

        assertEquals(List.of("after1", "after2"), service.drainSince(watermark));
    }

    @Test
    void drainSince_noNewLinesSinceNewWatermark_returnsEmpty() {
        LogCaptureService service = new LogCaptureService(10);
        service.capture("before");

        long watermark = service.watermark();
        service.capture("after");

        // drainSince 是幂等查询（按水位），取走后缓冲仍在；用新水位查询才为空
        assertEquals(List.of("after"), service.drainSince(watermark));
        assertEquals(List.of(), service.drainSince(service.watermark()));
    }

    @Test
    void capture_beyondCapacity_evictsOldest() {
        LogCaptureService service = new LogCaptureService(3);
        service.capture("a");
        service.capture("b");
        service.capture("c");
        service.capture("d");

        assertEquals(3, service.size());
        long watermark = service.watermark();
        // 最老的 a 已被挤出缓冲，drainSince(0) 只能拿到仍在缓冲里的行
        assertEquals(List.of("b", "c", "d"), service.drainSince(0));
        assertEquals(4, watermark);
    }

    @Test
    void hasGapSince_noEviction_returnsFalse() {
        LogCaptureService service = new LogCaptureService(10);
        service.capture("a");
        service.capture("b");

        long watermark = service.watermark();
        service.capture("c");

        assertFalse(service.hasGapSince(watermark));
    }

    @Test
    void hasGapSince_evictionBetweenWatermarkAndBufferStart_returnsTrue() {
        LogCaptureService service = new LogCaptureService(3);
        service.capture("a"); // seq=1
        service.capture("b"); // seq=2

        long watermark = service.watermark(); // 2
        service.capture("c"); // 3
        service.capture("d"); // 4（驱逐 a）
        service.capture("e"); // 5（驱逐 b）
        service.capture("f"); // 6（驱逐 c —— 水位 2 之后的行开始丢失）

        // 水位 2 之后的行 c(3) 已被驱逐，缓冲起点 seq=4
        assertTrue(service.hasGapSince(watermark));
        assertEquals(List.of("d", "e", "f"), service.drainSince(watermark));
    }

    @Test
    void hasGapSince_noNewLines_returnsFalse() {
        LogCaptureService service = new LogCaptureService(3);
        service.capture("a");
        service.capture("b");

        long watermark = service.watermark();
        assertFalse(service.hasGapSince(watermark));
    }

    @Test
    void hasGapSince_exactBoundaryNoEviction_returnsFalse() {
        // 边界：缓冲恰好从水位之后的首行开始（oldest == fromSeq + 1），无丢失
        LogCaptureService service = new LogCaptureService(3);
        service.capture("a"); // seq=1
        service.capture("b"); // seq=2

        long watermark = service.watermark(); // 2
        service.capture("c"); // seq=3 —— 驱逐尚未发生

        assertFalse(service.hasGapSince(watermark));
        assertEquals(List.of("c"), service.drainSince(watermark));
    }

    @Test
    void constructor_negativeCapacity_throws() {
        assertThrows(IllegalArgumentException.class, () -> new LogCaptureService(0));
        assertThrows(IllegalArgumentException.class, () -> new LogCaptureService(-1));
    }

    @Test
    void capture_fromMultipleThreads_noDataLoss() throws Exception {
        LogCaptureService service = new LogCaptureService(1000);
        int threads = 4;
        int linesPerThread = 200;
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            workers[t] = new Thread(() -> {
                for (int i = 0; i < linesPerThread; i++) {
                    service.capture("t" + threadId + "-" + i);
                }
            });
            workers[t].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        assertEquals(threads * linesPerThread, service.size());
        assertEquals(threads * linesPerThread, service.watermark());
        assertEquals(threads * linesPerThread, service.drainSince(0).size());
    }
}
