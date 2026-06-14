package com.jokerhub.paper.plugin.orzmc.features.guide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.server.OrzUtil;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

class GuideServiceTest {

    private ServerFacade serverFacade;
    private ConfigService configService;
    private OrzTextStyles styles;
    private org.bukkit.Server server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private GuideService guideService;

    private MockedStatic<OrzUtil> orzUtilMock;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        serverFacade = mock(ServerFacade.class);
        server = mock(org.bukkit.Server.class);
        plugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
        configService = mock(ConfigService.class);
        styles = mock(OrzTextStyles.class);

        when(serverFacade.server()).thenReturn(server);
        when(serverFacade.plugin()).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(Logger.getGlobal());

        // Mock Bukkit.getItemFactory() for ItemStack meta creation
        ItemFactory itemFactory = mock(ItemFactory.class);
        BookMeta bookMeta = mock(BookMeta.class);
        when(itemFactory.getItemMeta(Material.WRITTEN_BOOK)).thenReturn(bookMeta);
        when(itemFactory.asMetaFor(any(ItemMeta.class), any(Material.class))).thenReturn(bookMeta);
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getItemFactory).thenReturn(itemFactory);

        orzUtilMock = mockStatic(OrzUtil.class);

        guideService = new GuideService(serverFacade, configService, styles);
    }

    @AfterEach
    void tearDown() {
        orzUtilMock.close();
        bukkitMock.close();
    }

    private YamlConfiguration createGuideConfig(
            boolean enable, String title, String author, java.util.List<Map<?, ?>> content) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enable", enable);
        yaml.set("title", title);
        yaml.set("author", author);
        if (content != null) {
            yaml.set("content", content);
        }
        return yaml;
    }

    private Map<String, Object> textContentItem(String text) {
        Map<String, Object> item = new HashMap<>();
        Map<String, Object> textMap = new HashMap<>();
        textMap.put("content", text);
        item.put("text", textMap);
        return item;
    }

    @ParameterizedTest
    @CsvSource({
        "false, Test, Server, null",    // disabled config
        "true, 指南, 服主, WRITTEN_BOOK", // enabled with content
    })
    void buildGuideBook_parameterized(boolean enable, String title, String author, String expectedType) {
        java.util.List<Map<?, ?>> content = new ArrayList<>();
        if (enable) {
            content.add(textContentItem("Hello World"));
        }
        YamlConfiguration yaml = createGuideConfig(enable, title, author, content);
        when(configService.getConfig("guide_book")).thenReturn(yaml);

        ItemStack result = guideService.buildGuideBook();

        if ("null".equals(expectedType)) {
            assertNull(result);
        } else {
            assertNotNull(result);
            assertEquals(Material.valueOf(expectedType), result.getType());
        }
    }

    @Test
    void buildGuideBook_configNotParsable_returnsNull() {
        when(configService.getConfig("guide_book")).thenReturn(null);

        ItemStack result = guideService.buildGuideBook();

        assertNull(result);
    }

    @Test
    void openGuide_bookDisabled_sendsFailure() {
        YamlConfiguration yaml = createGuideConfig(false, "Test", "Server", new ArrayList<>());
        when(configService.getConfig("guide_book")).thenReturn(yaml);
        orzUtilMock.when(() -> OrzUtil.failureText(eq(styles), eq("服主未配置新手指南"))).thenReturn(Component.text("failed"));

        Player player = mock(Player.class);
        guideService.openGuide(player);

        verify(player).sendMessage(Component.text("failed"));
        verify(player, never()).openBook(any(ItemStack.class));
    }

    @Test
    void openGuide_bookEnabled_opensBook() {
        java.util.List<Map<?, ?>> content = new ArrayList<>();
        content.add(textContentItem("Welcome"));

        YamlConfiguration yaml = createGuideConfig(true, "Guide", "Server", content);
        when(configService.getConfig("guide_book")).thenReturn(yaml);

        Player player = mock(Player.class);
        guideService.openGuide(player);

        verify(player).openBook(any(ItemStack.class));
    }

    @Test
    void giveIfFirstJoin_hasPlayedBefore_doesNothing() {
        java.util.List<Map<?, ?>> content = new ArrayList<>();
        content.add(textContentItem("Welcome"));

        YamlConfiguration yaml = createGuideConfig(true, "Guide", "Server", content);
        when(configService.getConfig("guide_book")).thenReturn(yaml);

        Player player = mock(Player.class);
        com.destroystokyo.paper.profile.PlayerProfile profile =
                mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);

        when(player.getPlayerProfile()).thenReturn(profile);
        when(profile.getId()).thenReturn(UUID.randomUUID());
        when(server.getOfflinePlayer(any(UUID.class))).thenReturn(offlinePlayer);
        when(offlinePlayer.hasPlayedBefore()).thenReturn(true);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));

        guideService.giveIfFirstJoin(player);

        verify(player.getInventory(), never()).addItem(any());
    }

    @Test
    void giveIfFirstJoin_firstJoin_givesBook() {
        java.util.List<Map<?, ?>> content = new ArrayList<>();
        content.add(textContentItem("Welcome"));

        YamlConfiguration yaml = createGuideConfig(true, "Guide", "Server", content);
        when(configService.getConfig("guide_book")).thenReturn(yaml);
        when(styles.success(anyString())).thenReturn(Component.text("获得新手指南"));

        Player player = mock(Player.class);
        com.destroystokyo.paper.profile.PlayerProfile profile =
                mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
        PlayerInventory inventory = mock(PlayerInventory.class);

        when(player.getPlayerProfile()).thenReturn(profile);
        when(profile.getId()).thenReturn(UUID.randomUUID());
        when(server.getOfflinePlayer(any(UUID.class))).thenReturn(offlinePlayer);
        when(offlinePlayer.hasPlayedBefore()).thenReturn(false);
        when(player.getInventory()).thenReturn(inventory);

        orzUtilMock
                .when(() -> OrzUtil.successText(eq(styles), anyString()))
                .thenAnswer(i -> ((OrzTextStyles) i.getArgument(0)).success(i.getArgument(1)));

        guideService.giveIfFirstJoin(player);

        verify(inventory).addItem(any(ItemStack.class));
        verify(player).sendMessage(Component.text("获得新手指南"));
    }
}
