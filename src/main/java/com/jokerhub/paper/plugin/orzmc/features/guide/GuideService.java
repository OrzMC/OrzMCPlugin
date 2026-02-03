package com.jokerhub.paper.plugin.orzmc.features.guide;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.GuideBookConfigParser;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.ContentItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuideBookConfig;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.LinkContent;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextContent;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextStyle;
import com.jokerhub.paper.plugin.orzmc.infra.server.OrzUtil;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public final class GuideService {
    private final ConfigService configService;
    private final OrzTextStyles styles;

    public GuideService(ConfigService configService, OrzTextStyles styles) {
        this.configService = configService;
        this.styles = styles;
    }

    public ItemStack buildGuideBook() {
        GuideBookConfigParser parser = new GuideBookConfigParser(OrzMC.plugin(), configService);
        GuideBookConfig cfg = parser.parseConfig();
        if (cfg == null || !cfg.enable()) return null;
        ItemStack guideBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) guideBook.getItemMeta();
        bookMeta.setTitle(cfg.title());
        bookMeta.setAuthor(cfg.author());
        bookMeta.setGeneration(BookMeta.Generation.COPY_OF_COPY);
        ArrayList<TextComponent> pages = new ArrayList<>();
        TextComponent.Builder pageBuilder = Component.text();
        for (ContentItem item : cfg.content()) {
            TextComponent.Builder t = Component.text();
            if (item.isText()) {
                TextContent textItem = item.getText();
                if (!textItem.content().isEmpty()) {
                    t.append(Component.text(textItem.content()));
                }
            } else if (item.isLink()) {
                LinkContent linkItem = item.getLink();
                TextComponent.Builder linkTextBuilder = Component.text();
                if (!linkItem.content().isEmpty()) {
                    linkTextBuilder.append(Component.text(linkItem.content()));
                }
                if (!linkItem.url().isEmpty()) {
                    Style defaultLinkStyle = Style.style()
                            .color(TextColor.fromCSSHexString("#5555FF"))
                            .build();
                    linkTextBuilder.style(defaultLinkStyle);
                    linkTextBuilder.clickEvent(ClickEvent.openUrl(linkItem.url()));
                    linkTextBuilder.hoverEvent(HoverEvent.showText(Component.text(linkItem.hoverText())));
                }
                t.append(linkTextBuilder.build());
            }
            if (item.getStyle() != null) {
                TextStyle style = item.getStyle();
                if (style.getBold()) {
                    t.decorate(TextDecoration.BOLD);
                }
                if (style.getUnderlined()) {
                    t.decorate(TextDecoration.UNDERLINED);
                }
                if (!style.getColor().isEmpty()) {
                    TextColor textColor = TextColor.fromCSSHexString(style.getColor());
                    t.color(textColor);
                }
            }
            Collections.nCopies(item.getNewlineCount(), Component.newline()).forEach(t::append);
            TextComponent textComponent = t.build();
            pageBuilder.append(textComponent);
            if (item.getPageBreak()) {
                pages.add(pageBuilder.build());
                pageBuilder = Component.text();
            }
        }
        TextComponent last = pageBuilder.build();
        if (!last.children().isEmpty()) {
            pages.add(last);
        }
        bookMeta.addPages(pages.toArray(new TextComponent[0]));
        guideBook.setItemMeta(bookMeta);
        return guideBook;
    }

    public void openGuide(Player player) {
        ItemStack guideBook = buildGuideBook();
        if (guideBook == null) {
            player.sendMessage(OrzUtil.failureText(styles, "服主未配置新手指南"));
            return;
        }
        player.openBook(guideBook);
    }

    public void giveIfFirstJoin(Player player) {
        UUID playerUUID = player.getPlayerProfile().getId();
        if (playerUUID == null) return;
        OfflinePlayer offlinePlayer = OrzMC.server().getOfflinePlayer(playerUUID);
        if (!offlinePlayer.hasPlayedBefore()) {
            ItemStack guideBook = buildGuideBook();
            if (guideBook != null) {
                player.getInventory().addItem(guideBook);
                player.sendMessage(OrzUtil.successText(styles, "获得新手指南"));
            }
        }
    }
}
