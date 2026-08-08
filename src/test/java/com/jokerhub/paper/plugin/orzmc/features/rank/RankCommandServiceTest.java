package com.jokerhub.paper.plugin.orzmc.features.rank;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.features.review.ReviewService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewType;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * /rank 状态展示按当前权限组动态化：
 *
 * <ul>
 *   <li>default：显示自动晋升进度 + 引导（下次上线自动晋升）</li>
 *   <li>member：显示时长达标 + 下一步可申请（注册表反向生成）</li>
 *   <li>builder/admin：不再展示已完成的 member 阈值，给出「暂无更高项/最高等级」</li>
 * </ul>
 */
class RankCommandServiceTest {

    private RankService service;
    private ReviewService reviewService;
    private OrzTextStyles styles;
    private RankCommandService command;

    @BeforeEach
    void setUp() {
        service = mock(RankService.class);
        reviewService = mock(ReviewService.class);
        ConfigService configService = mock(ConfigService.class);
        FileConfiguration templatesConfig = mock(FileConfiguration.class);
        when(configService.getConfig("templates")).thenReturn(templatesConfig);
        when(templatesConfig.getConfigurationSection("styles")).thenReturn(null);
        when(configService.loadFile("styles.yml")).thenReturn(null);
        styles = new OrzTextStyles(configService);
        command = new RankCommandService(service, reviewService, styles);
    }

    private String text(UUID id) {
        var result = command.statusOf(id);
        assertTrue(result instanceof RankCommandService.Result.Success);
        return PlainTextComponentSerializer.plainText()
                .serialize(((RankCommandService.Result.Success) result).message());
    }

    @Test
    void default_belowThreshold_showsProgressAndAutoPromoteHint() {
        UUID id = UUID.randomUUID();
        when(service.currentGroup(id)).thenReturn("default");
        when(service.playtimeMinutes(id)).thenReturn(30L);
        when(service.memberThresholdMinutes()).thenReturn(600L);

        String t = text(id);
        assertTrue(t.contains("访客（default）"));
        assertTrue(t.contains("还需 570 分钟"));
        assertTrue(t.contains("自动晋升为成员"));
        assertFalse(t.contains("可申请"));
    }

    @Test
    void default_reachedThreshold_showsReadyHint() {
        UUID id = UUID.randomUUID();
        when(service.currentGroup(id)).thenReturn("default");
        when(service.playtimeMinutes(id)).thenReturn(700L);
        when(service.memberThresholdMinutes()).thenReturn(600L);

        String t = text(id);
        assertTrue(t.contains("✅ 已达标（下次上线将自动晋升为成员）"));
    }

    @Test
    void member_showsThresholdMetAndNextApplication() {
        UUID id = UUID.randomUUID();
        when(service.currentGroup(id)).thenReturn("member");
        when(service.playtimeMinutes(id)).thenReturn(700L);
        when(service.memberThresholdMinutes()).thenReturn(600L);
        ReviewType builder = new ReviewType(
                "builder-promotion",
                "晋升建造者",
                "builder",
                raw -> java.util.Map.of(),
                p -> true,
                data -> "申请晋升 builder",
                null);
        when(reviewService.registeredTypes()).thenReturn(List.of(builder));

        String t = text(id);
        assertTrue(t.contains("成员（member）"));
        assertTrue(t.contains("✅ 已达标"));
        assertTrue(t.contains("下一步可申请：晋升建造者（/apply builder）"));
    }

    @Test
    void builder_showsNextApplicationToAdmin() {
        // 四级流转：builder 下一步可申请晋升 admin（非「暂无更高」）
        UUID id = UUID.randomUUID();
        when(service.currentGroup(id)).thenReturn("builder");
        when(service.playtimeMinutes(id)).thenReturn(5000L);
        ReviewType adminType = new ReviewType(
                "admin-promotion", "晋升管理员", "admin", raw -> java.util.Map.of(), p -> true, data -> "申请晋升 admin", null);
        when(reviewService.registeredTypes()).thenReturn(List.of(adminType));

        String t = text(id);
        assertTrue(t.contains("建造者（builder）"));
        assertFalse(t.contains("晋升成员阈值")); // 已完成的阈值不再展示
        assertTrue(t.contains("下一步可申请：晋升管理员（/apply admin）"));
    }

    @Test
    void admin_showsTopLevel() {
        UUID id = UUID.randomUUID();
        when(service.currentGroup(id)).thenReturn("admin");
        when(service.playtimeMinutes(id)).thenReturn(9999L);

        String t = text(id);
        assertTrue(t.contains("管理员（admin）"));
        assertTrue(t.contains("已达最高等级（管理员）"));
    }
}
