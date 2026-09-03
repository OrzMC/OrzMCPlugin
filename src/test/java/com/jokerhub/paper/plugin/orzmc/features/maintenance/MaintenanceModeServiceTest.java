package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceModeService.MaintenanceProgress;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceModeService.MaintenanceReason;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import org.junit.jupiter.api.Test;

class MaintenanceModeServiceTest extends ServiceTestBase {

    @Test
    void initialState_isInactiveWithNoReason() {
        MaintenanceModeService svc = new MaintenanceModeService();
        assertFalse(svc.isActive());
        assertNull(svc.reason());
        assertEquals(0L, svc.startedAt());
        assertNull(svc.progress());
    }

    @Test
    void enter_setsReasonStartedAtAndClearsProgress() {
        MaintenanceModeService svc = new MaintenanceModeService();
        svc.updateProgress("区块", 10, 5); // 上一次残留进度
        svc.enter(MaintenanceReason.BACKUP);

        assertTrue(svc.isActive());
        assertEquals(MaintenanceReason.BACKUP, svc.reason());
        assertTrue(svc.startedAt() > 0);
        assertNull(svc.progress(), "enter 应清空上一次进度");
    }

    @Test
    void updateProgress_buildsImmutableSnapshotWithMessage() {
        MaintenanceModeService svc = new MaintenanceModeService();
        svc.enter(MaintenanceReason.OPTIMIZE);
        svc.updateProgress("区域", 45, 35);

        MaintenanceProgress p = svc.progress();
        assertNotNull(p);
        assertEquals("区域", p.stage());
        assertEquals(45, p.percent());
        assertEquals(35, p.etaSeconds());
        assertEquals("进度：区域 45% 预计剩余 35秒", p.progressMessage());
    }

    @Test
    void updateProgress_negativeEtaClampedToZero() {
        MaintenanceModeService svc = new MaintenanceModeService();
        svc.enter(MaintenanceReason.BACKUP);
        svc.updateProgress("文件", 99, -3);

        MaintenanceProgress p = svc.progress();
        assertEquals(0, p.etaSeconds());
        assertTrue(p.progressMessage().contains("0秒"));
    }

    @Test
    void exit_clearsAllState() {
        MaintenanceModeService svc = new MaintenanceModeService();
        svc.enter(MaintenanceReason.MANUAL);
        svc.updateProgress("完成", 100, 0);

        svc.exit();

        assertFalse(svc.isActive());
        assertNull(svc.reason());
        assertNull(svc.progress());
    }

    // ===== renderTemplate（占位符替换 + 无进度兜底） =====

    @Test
    void renderTemplate_replacesAllPlaceholders() {
        MaintenanceProgress p = MaintenanceProgress.of("区域", 30, 60);
        assertEquals("进度 区域 30 60", MaintenanceModeService.renderTemplate("进度 {stage} {percent} {eta}", p));
    }

    @Test
    void renderTemplate_withoutProgress_replacesPlaceholdersWithEmpty() {
        // PR4 兜底：无进度（manual/刚进入）时不再把 {stage}/{percent}/{eta} 当字面量显示，
        // 而是替换为空串——消除自定义纯文案模板露出的 "{percent}" 残留。
        assertEquals("A[][][]C", MaintenanceModeService.renderTemplate("A[{stage}][{percent}][{eta}]C", null));
    }

    @Test
    void renderTemplate_nullTemplate_returnsEmpty() {
        assertEquals("", MaintenanceModeService.renderTemplate(null, MaintenanceProgress.of("区域", 1, 1)));
    }

    @Test
    void renderTemplate_nullStageKeepsStagePlaceholder() {
        MaintenanceProgress p = new MaintenanceProgress(null, 42, 7, "msg");
        assertEquals("stage={stage} 42 7", MaintenanceModeService.renderTemplate("stage={stage} {percent} {eta}", p));
    }

    // ===== renderMotdText（统一渲染入口：三场景 × 有/无进度 × 占位符） =====

    @Test
    void renderMotdText_nullReason_returnsGenericFallback() {
        assertEquals("服务器维护中，请稍后再尝试登录。", MaintenanceModeService.renderMotdText(null, defaultTemplates(), null));
    }

    @Test
    void renderMotdText_backup_noProgress_returnsSceneOnly() {
        assertEquals(
                "服务器地图备份中，请稍后再试",
                MaintenanceModeService.renderMotdText(
                        MaintenanceReason.BACKUP, maintenanceTemplates("服务器地图备份中，请稍后再试", null, null, null), null));
    }

    @Test
    void renderMotdText_optimize_noProgress_returnsSceneOnly() {
        assertEquals(
                "服务器地图优化中，请稍后再试",
                MaintenanceModeService.renderMotdText(
                        MaintenanceReason.OPTIMIZE, maintenanceTemplates(null, "服务器地图优化中，请稍后再试", null, null), null));
    }

    @Test
    void renderMotdText_manual_noProgress_returnsSceneOnly() {
        assertEquals(
                "服务器维护中，请稍后再试",
                MaintenanceModeService.renderMotdText(
                        MaintenanceReason.MANUAL, maintenanceTemplates(null, null, "服务器维护中，请稍后再试", null), null));
    }

    @Test
    void renderMotdText_manual_withProgress_omitsProgressLine() {
        // MANUAL 场景永远不追加进度行（服主手动进入无备份/优化进度可展示）
        MaintenanceProgress p = MaintenanceProgress.of("区块", 35, 30);
        assertEquals(
                "手动维护中",
                MaintenanceModeService.renderMotdText(
                        MaintenanceReason.MANUAL, maintenanceTemplates(null, null, "手动维护中", "进度 {percent}%"), p));
    }

    @Test
    void renderMotdText_sceneNoPlaceholders_withProgress_appendsDefaultProgressLine() {
        // 纯场景文案 + 有进度 → 追加 progress_line（默认模板「进度：{stage} {percent}% 预计剩余 {eta}秒」）为第二行
        MaintenanceProgress p = MaintenanceProgress.of("区块", 35, 30);
        assertEquals(
                "备份维护中\n进度：区块 35% 预计剩余 30秒",
                MaintenanceModeService.renderMotdText(
                        MaintenanceReason.BACKUP, maintenanceTemplates("备份维护中", null, null, null), p));
    }

    @Test
    void renderMotdText_sceneNoPlaceholders_withProgress_customProgressLine() {
        MaintenanceProgress p = MaintenanceProgress.of("区块", 35, 30);
        assertEquals(
                "备份维护中\n剩余 30秒 (35%)",
                MaintenanceModeService.renderMotdText(
                        MaintenanceReason.OPTIMIZE,
                        maintenanceTemplates(null, "备份维护中", null, "剩余 {eta}秒 ({percent}%)"),
                        p));
    }

    @Test
    void renderMotdText_sceneHasPlaceholders_withProgress_noSeparateProgressLine() {
        // 场景模板自带进度占位符 → 占位符渲染进场景文案，不再追加独立进度行（防两行重复）
        MaintenanceProgress p = MaintenanceProgress.of("区块", 35, 30);
        String text = MaintenanceModeService.renderMotdText(
                MaintenanceReason.BACKUP, maintenanceTemplates("备份 {stage} {percent}% 预计 {eta}秒", null, null, null), p);
        assertEquals("备份 区块 35% 预计 30秒", text);
        assertFalse(text.contains("\n"), "场景模板含占位符时不应追加第二行进度: " + text);
    }

    @Test
    void renderMotdText_sceneHasPlaceholders_withoutProgress_stripsPlaceholders() {
        // 自定义场景模板带进度占位符但无进度 → 占位符替换为空串（不留字面量），且不追加进度行
        assertEquals(
                "备份  % 预计 秒",
                MaintenanceModeService.renderMotdText(
                        MaintenanceReason.BACKUP,
                        maintenanceTemplates("备份 {stage} {percent}% 预计 {eta}秒", null, null, null),
                        null));
    }

    @Test
    void renderMotdText_sceneNoPlaceholders_withProgress_etaZero_appendsZeroEtaLine() {
        // 边界：eta=0（已完成/瞬间）→ progress_line 仍渲染「预计剩余 0秒」（不因 0 吞行）
        MaintenanceProgress p = MaintenanceProgress.of("区块", 100, 0);
        assertEquals(
                "备份维护中\n进度：区块 100% 预计剩余 0秒",
                MaintenanceModeService.renderMotdText(
                        MaintenanceReason.BACKUP, maintenanceTemplates("备份维护中", null, null, null), p));
    }

    @Test
    void renderMotdText_sceneOnlyStagePlaceholder_withProgress_noSeparateProgressLine() {
        // 边界：场景模板仅含 {stage} → hasProgressPlaceholders 判定为真，占位符替换进场景文案，
        // 不再追加独立进度行（percent/eta 不在模板中，不渲染）
        MaintenanceProgress p = MaintenanceProgress.of("区块", 35, 30);
        assertEquals(
                "正在处理区块",
                MaintenanceModeService.renderMotdText(
                        MaintenanceReason.BACKUP, maintenanceTemplates("正在处理{stage}", null, null, null), p));
    }

    @Test
    void renderMotdText_optimize_withProgress_endToEnd() {
        // 端到端：OPTIMIZE 场景纯文案 + 有进度 → 场景行 + 默认 progress_line 第二行
        MaintenanceProgress p = MaintenanceProgress.of("区块", 50, 20);
        assertEquals(
                "服务器地图优化中，请稍后再试\n进度：区块 50% 预计剩余 20秒",
                MaintenanceModeService.renderMotdText(
                        MaintenanceReason.OPTIMIZE, maintenanceTemplates(null, "服务器地图优化中，请稍后再试", null, null), p));
    }
}
