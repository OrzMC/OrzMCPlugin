package com.jokerhub.paper.plugin.orzmc.features.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.UUID;
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
}
