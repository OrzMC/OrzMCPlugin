package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 入站去重单测：TTL 窗口内同 id 只放行一次、过期后重新放行、无 id fail-open、容量有界。
 */
class InboundDedupTest {

    @Test
    void firstSeen_trueOnceThenFalseWithinTtl() {
        InboundDedup dedup = new InboundDedup(60_000);
        assertTrue(dedup.firstSeen("m1"), "首次应放行");
        assertFalse(dedup.firstSeen("m1"), "窗口内重复应丢弃（防平台重放重复执行命令）");
        assertTrue(dedup.firstSeen("m2"), "不同 id 互不影响");
    }

    @Test
    void firstSeen_allowsAgainAfterTtl() throws Exception {
        InboundDedup dedup = new InboundDedup(50);
        assertTrue(dedup.firstSeen("m1"));
        assertFalse(dedup.firstSeen("m1"));
        Thread.sleep(80);
        assertTrue(dedup.firstSeen("m1"), "过期后应重新放行（平台消息 id 不复用，超窗再出现按新消息处理）");
    }

    @Test
    void firstSeen_failOpenWithoutId() {
        InboundDedup dedup = new InboundDedup(60_000);
        assertTrue(dedup.firstSeen(null), "无 id 不去重（不因缺字段吞消息）");
        assertTrue(dedup.firstSeen(""));
        assertTrue(dedup.firstSeen("   "));
        assertEquals(0, dedup.size());
    }

    @Test
    void firstSeen_boundedUnderFlood() {
        InboundDedup dedup = new InboundDedup(60_000);
        for (int i = 0; i < InboundDedup.MAX_ENTRIES * 3; i++) {
            dedup.firstSeen("flood-" + i);
        }
        assertTrue(dedup.size() <= InboundDedup.MAX_ENTRIES, "去重表必须有界: " + dedup.size());
    }

    @Test
    void defaultTtl_appliedWhenNonPositive() {
        InboundDedup dedup = new InboundDedup(0);
        assertTrue(dedup.firstSeen("m1"));
        assertFalse(dedup.firstSeen("m1"), "非法 ttl 应按默认窗口兜底（仍生效）");
    }
}
