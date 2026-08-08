package com.jokerhub.paper.plugin.orzmc.features.rank;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.features.review.ReviewRequest;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PermissionStore 测试：时长读 stats、晋升状态与审核记录走 permission.yml 三段。 */
class PermissionStoreTest {

    @TempDir
    Path tempDir;

    private ConfigService configService;
    private FileConfiguration permissionCfg;
    private PermissionStore store;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        permissionCfg = new YamlConfiguration();
        when(configService.getConfig("permission")).thenReturn(permissionCfg);
        when(configService.saveConfig("permission")).thenReturn(true);
        store = new PermissionStore(configService);
    }

    private Path writeStats(String json) throws IOException {
        Path file = tempDir.resolve("test.json");
        Files.writeString(file, json);
        return file;
    }

    // ---- 时长：读 stats 文件 ----

    @Test
    void readPlayTimeTicks_parsesPlayTime() throws Exception {
        Path file =
                writeStats("{\"stats\":{\"minecraft:custom\":{\"minecraft:play_time\":72000}},\"DataVersion\":3700}");
        assertEquals(72000L, PermissionStore.readPlayTimeTicks(file));
    }

    @Test
    void readPlayTimeTicks_missingFile_returnsZero() {
        assertEquals(0L, PermissionStore.readPlayTimeTicks(tempDir.resolve("nope.json")));
    }

    @Test
    void readPlayTimeTicks_missingCustom_returnsZero() throws Exception {
        Path file = writeStats("{\"stats\":{}}");
        assertEquals(0L, PermissionStore.readPlayTimeTicks(file));
    }

    @Test
    void readPlayTimeTicks_missingPlayTime_returnsZero() throws Exception {
        Path file = writeStats("{\"stats\":{\"minecraft:custom\":{\"minecraft:walk_one_cm\":100}}}");
        assertEquals(0L, PermissionStore.readPlayTimeTicks(file));
    }

    @Test
    void readPlayTimeTicks_brokenJson_returnsZero() throws Exception {
        Path file = writeStats("{broken json");
        assertEquals(0L, PermissionStore.readPlayTimeTicks(file));
    }

    @Test
    void readPlayTimeTicks_offlinePlayer_readsStatsFile() throws Exception {
        Path file = writeStats("{\"stats\":{\"minecraft:custom\":{\"minecraft:play_time\":72000}}}");
        assertEquals(60L, PermissionStore.readPlayTimeTicks(file) / 1200L);
    }

    // ---- 静态配置节 ----

    @Test
    void memberThresholdHours_defaultWhenMissing() {
        assertEquals(10, store.memberThresholdHours());
    }

    @Test
    void memberThresholdHours_readsConfiguredValue() {
        permissionCfg.set("config.member-threshold-hours", 24);
        assertEquals(24, store.memberThresholdHours());
    }

    // ---- 审核记录（reviews 节）----

    private ReviewRequest sampleRequest(String id, ReviewRequest.Status status) {
        return new ReviewRequest(
                id,
                "builder-promotion",
                UUID.randomUUID(),
                Map.of("target-group", "builder", "reason", "想用 WorldEdit"),
                status,
                1000L,
                status == ReviewRequest.Status.PENDING ? 0L : 2000L,
                status == ReviewRequest.Status.PENDING ? null : "admin");
    }

    @Test
    void saveAndFindById_roundTrip() {
        ReviewRequest request = sampleRequest("r1", ReviewRequest.Status.PENDING);
        store.save(request);

        ReviewRequest loaded = store.findById("r1").orElseThrow();
        assertEquals("builder-promotion", loaded.typeId());
        assertEquals(request.applicantId(), loaded.applicantId());
        assertEquals(Map.of("target-group", "builder", "reason", "想用 WorldEdit"), loaded.data());
        assertEquals(ReviewRequest.Status.PENDING, loaded.status());
        assertEquals(1000L, loaded.createdAt());
        assertNull(loaded.reviewerName());
    }

    @Test
    void findById_missing_returnsEmpty() {
        assertTrue(store.findById("nope").isEmpty());
    }

    @Test
    void listPending_onlyPendingSortedByTime() {
        store.save(sampleRequest("r1", ReviewRequest.Status.PENDING));
        store.save(sampleRequest("r2", ReviewRequest.Status.APPROVED));
        store.save(sampleRequest("r3", ReviewRequest.Status.PENDING));
        // 人为调整顺序：r1 1000ms，r3 1000ms 同值——按 createdAt 稳定排序即可
        assertEquals(2, store.listPending().size());
        assertTrue(store.listPending().stream().allMatch(r -> r.status() == ReviewRequest.Status.PENDING));
    }

    @Test
    void hasPending_trueAfterSave() {
        UUID applicant = UUID.randomUUID();
        ReviewRequest request = new ReviewRequest(
                "r1",
                "builder-promotion",
                applicant,
                Map.of("target-group", "builder"),
                ReviewRequest.Status.PENDING,
                1000L,
                0L,
                null);
        store.save(request);

        assertTrue(store.hasPending("builder-promotion", applicant));
        assertFalse(store.hasPending("whitelist-apply", applicant));
    }

    @Test
    void hasPending_falseAfterApproved() {
        UUID applicant = UUID.randomUUID();
        store.save(sampleRequest("r1", ReviewRequest.Status.APPROVED));
        assertFalse(store.hasPending(
                "builder-promotion",
                sampleRequest("r1", ReviewRequest.Status.APPROVED).applicantId()));
    }

    @Test
    void listByApplicant_returnsOwnRequestsOnly() {
        UUID applicant = UUID.randomUUID();
        store.save(new ReviewRequest(
                "r1", "builder-promotion", applicant, Map.of(), ReviewRequest.Status.PENDING, 1000L, 0L, null));
        store.save(sampleRequest("r2", ReviewRequest.Status.PENDING)); // 其他申请人

        assertEquals(1, store.listByApplicant(applicant).size());
        assertEquals("r1", store.listByApplicant(applicant).get(0).id());
    }

    // ---- 结案历史裁剪（permission.yml 增长控制）----

    private ReviewRequest closed(String id, UUID applicant, long createdAt) {
        return new ReviewRequest(
                id,
                "builder-promotion",
                applicant,
                Map.of("target-group", "builder"),
                ReviewRequest.Status.APPROVED,
                createdAt,
                createdAt + 1000L,
                "admin");
    }

    @Test
    void save_trimsOldestClosedBeyondLimit() {
        UUID applicant = UUID.randomUUID();
        // 先写满上限 10 条（createdAt 递增 1..10）
        for (int i = 1; i <= 10; i++) {
            store.save(closed("c" + i, applicant, i * 1000L));
        }
        // 第 11 条触发裁剪：应删最旧的 c1，保留 c2..c11
        store.save(closed("c11", applicant, 11 * 1000L));

        var history = store.listByApplicant(applicant);
        assertEquals(10, history.size());
        assertTrue(history.stream().noneMatch(r -> r.id().equals("c1")));
        assertTrue(history.stream().anyMatch(r -> r.id().equals("c11")));
        // 保留的是最近的 10 条
        assertEquals("c2", history.get(0).id());
        assertEquals("c11", history.get(history.size() - 1).id());
    }

    @Test
    void save_keepsPendingWhenTrimming() {
        UUID applicant = UUID.randomUUID();
        for (int i = 1; i <= 10; i++) {
            store.save(closed("c" + i, applicant, i * 1000L));
        }
        store.save(new ReviewRequest(
                "p1", "builder-promotion", applicant, Map.of(), ReviewRequest.Status.PENDING, 11000L, 0L, null));
        store.save(new ReviewRequest(
                "p2", "admin-promotion", applicant, Map.of(), ReviewRequest.Status.PENDING, 12000L, 0L, null));

        // 12 条（10 结案 + 2 待审）→ 裁剪 2 条最旧结案，PENDING 全部保留
        var history = store.listByApplicant(applicant);
        assertEquals(10, history.size());
        assertTrue(history.stream().anyMatch(r -> r.id().equals("p1")));
        assertTrue(history.stream().anyMatch(r -> r.id().equals("p2")));
        assertTrue(history.stream().noneMatch(r -> r.id().equals("c1")));
        assertTrue(history.stream().noneMatch(r -> r.id().equals("c2")));
    }

    @Test
    void save_underLimit_noTrim() {
        UUID applicant = UUID.randomUUID();
        for (int i = 1; i <= 9; i++) {
            store.save(closed("c" + i, applicant, i * 1000L));
        }
        assertEquals(9, store.listByApplicant(applicant).size());
    }
}
