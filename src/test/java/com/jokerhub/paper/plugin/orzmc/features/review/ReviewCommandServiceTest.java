package com.jokerhub.paper.plugin.orzmc.features.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** /apply 列表按当前玩家资格过滤（已是 builder 不再提示申请 builder）。 */
class ReviewCommandServiceTest {

    private ReviewService reviewService;
    private OrzTextStyles styles;
    private ReviewCommandService command;

    @BeforeEach
    void setUp() {
        reviewService = mock(ReviewService.class);
        ConfigService configService = mock(ConfigService.class);
        FileConfiguration templatesConfig = mock(FileConfiguration.class);
        when(configService.getConfig("templates")).thenReturn(templatesConfig);
        when(templatesConfig.getConfigurationSection("styles")).thenReturn(null);
        when(configService.loadFile("styles.yml")).thenReturn(null);
        styles = new OrzTextStyles(configService);
        command = new ReviewCommandService(reviewService, styles);
    }

    private ReviewType builderType(UUID id, boolean eligible) {
        return new ReviewType(
                "builder-promotion",
                "晋升建造者",
                "builder",
                raw -> java.util.Map.of(),
                p -> p.equals(id) && eligible,
                data -> "申请晋升 builder",
                null);
    }

    @Test
    void listTypes_eligiblePlayer_listsType() {
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(reviewService.registeredTypes()).thenReturn(List.of(builderType(id, true)));

        var result = command.listTypes(player);
        assertTrue(result instanceof ReviewCommandService.Result.Success);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Success) result).message());
        assertTrue(text.contains("晋升建造者"));
        assertTrue(text.contains("/apply builder"));
    }

    @Test
    void listTypes_ineligiblePlayer_returnsEmptyHint() {
        // 已是 builder（资格不满足 member）→ 不再提示申请 builder
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(reviewService.registeredTypes()).thenReturn(List.of(builderType(id, false)));

        var result = command.listTypes(player);
        assertTrue(result instanceof ReviewCommandService.Result.Failure);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Failure) result).message());
        assertTrue(text.contains("当前没有可申请的审核类型"));
        assertFalse(text.contains("晋升建造者"));
    }

    // ===== 补测（2026-08-19，review 覆盖率 65.9% → 目标 75%+）=====

    private Player mockPlayer(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    private ReviewRequest req(String typeId, ReviewRequest.Status status, String reviewer) {
        return new ReviewRequest("REQ-1", typeId, UUID.randomUUID(), java.util.Map.of(), status, 0L, 0L, reviewer);
    }

    @Test
    void apply_knownType_submitsAndReturnsSuccess() {
        UUID id = UUID.randomUUID();
        Player player = mockPlayer(id);
        when(reviewService.registeredTypes()).thenReturn(List.of(builderType(id, true)));
        when(reviewService.submit(any(), eq(id), any())).thenReturn(ReviewService.Result.ok("已提交申请", "REQ-1"));

        var result = command.apply(player, "builder", "想建大楼");
        assertTrue(result instanceof ReviewCommandService.Result.Success);
        verify(reviewService).submit(any(ReviewType.class), eq(id), any());
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Success) result).message());
        assertTrue(text.contains("已提交申请"));
    }

    @Test
    void apply_unknownType_returnsFailure() {
        Player player = mockPlayer(UUID.randomUUID());
        when(reviewService.registeredTypes()).thenReturn(List.of());

        var result = command.apply(player, "nope", "");
        assertTrue(result instanceof ReviewCommandService.Result.Failure);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Failure) result).message());
        assertTrue(text.contains("未知申请类型"));
        verify(reviewService, never()).submit(any(), any(), any());
    }

    @Test
    void apply_submitRejected_returnsFailure() {
        UUID id = UUID.randomUUID();
        Player player = mockPlayer(id);
        when(reviewService.registeredTypes()).thenReturn(List.of(builderType(id, true)));
        when(reviewService.submit(any(), eq(id), any())).thenReturn(ReviewService.Result.fail("已有待审申请"));

        var result = command.apply(player, "builder", "");
        assertTrue(result instanceof ReviewCommandService.Result.Failure);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Failure) result).message());
        assertTrue(text.contains("已有待审申请"));
    }

    @Test
    void status_noRequests_showsEmptyHint() {
        Player player = mockPlayer(UUID.randomUUID());
        when(reviewService.listByApplicant(any())).thenReturn(List.of());

        var result = command.status(player);
        assertTrue(result instanceof ReviewCommandService.Result.Success);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Success) result).message());
        assertTrue(text.contains("你还没有提交过申请"));
    }

    @Test
    void status_withRequests_showsStatusPerType() {
        UUID id = UUID.randomUUID();
        Player player = mockPlayer(id);
        when(reviewService.listByApplicant(id))
                .thenReturn(List.of(
                        req("builder", ReviewRequest.Status.PENDING, null),
                        req("builder", ReviewRequest.Status.APPROVED, "Steve")));
        when(reviewService.typeById("builder")).thenReturn(Optional.of(builderType(id, true)));

        var result = command.status(player);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Success) result).message());
        assertTrue(text.contains("待审核"));
        assertTrue(text.contains("已通过"));
        assertTrue(text.contains("Steve"), "已通过应显示审核人");
    }

    @Test
    void status_rejectedAndCancelled_showsText() {
        UUID id = UUID.randomUUID();
        Player player = mockPlayer(id);
        when(reviewService.listByApplicant(id))
                .thenReturn(List.of(
                        req("b1", ReviewRequest.Status.REJECTED, "Alice"),
                        req("b1", ReviewRequest.Status.CANCELLED, null)));
        when(reviewService.typeById(any())).thenReturn(Optional.empty()); // 类型已注销 → 显示 typeId

        var result = command.status(player);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Success) result).message());
        assertTrue(text.contains("已拒绝"));
        assertTrue(text.contains("Alice"));
        assertTrue(text.contains("已撤回"));
        assertTrue(text.contains("b1"), "类型已注销时回退显示 typeId");
    }

    @Test
    void cancel_knownType_returnsSuccess() {
        UUID id = UUID.randomUUID();
        Player player = mockPlayer(id);
        when(reviewService.registeredTypes()).thenReturn(List.of(builderType(id, true)));
        when(reviewService.cancelForApplicant(any(), eq(id))).thenReturn(ReviewService.Result.ok("已撤回", "REQ-1"));

        var result = command.cancel(player, "builder");
        assertTrue(result instanceof ReviewCommandService.Result.Success);
        verify(reviewService).cancelForApplicant(any(ReviewType.class), eq(id));
    }

    @Test
    void cancel_unknownType_returnsFailure() {
        Player player = mockPlayer(UUID.randomUUID());
        when(reviewService.registeredTypes()).thenReturn(List.of());

        var result = command.cancel(player, "nope");
        assertTrue(result instanceof ReviewCommandService.Result.Failure);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Failure) result).message());
        assertTrue(text.contains("未知申请类型"));
    }

    @Test
    void review_asyncResult_wrapsSuccess() {
        UUID id = UUID.randomUUID();
        Player admin = mockPlayer(id);
        when(reviewService.reviewByApplicantName("Bob", true, admin.getName()))
                .thenReturn(CompletableFuture.completedFuture(ReviewService.Result.ok("已通过 Bob 的申请", "REQ-9")));

        var result = command.review(admin, "Bob", true).join();
        assertTrue(result instanceof ReviewCommandService.Result.Success);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Success) result).message());
        assertTrue(text.contains("已通过 Bob"));
    }

    @Test
    void review_asyncResult_wrapsFailure() {
        UUID id = UUID.randomUUID();
        Player admin = mockPlayer(id);
        when(reviewService.reviewByApplicantName("Bob", false, admin.getName()))
                .thenReturn(CompletableFuture.completedFuture(ReviewService.Result.fail("Bob 没有待审申请")));

        var result = command.review(admin, "Bob", false).join();
        assertTrue(result instanceof ReviewCommandService.Result.Failure);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(((ReviewCommandService.Result.Failure) result).message());
        assertTrue(text.contains("Bob 没有待审申请"));
    }
}
