package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.utils.OrzUtil;
import com.jokerhub.paper.plugin.orzmc.utils.guidebook.GuideBookConfigParser;
import com.jokerhub.paper.plugin.orzmc.utils.guidebook.models.*;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrzGuideBook implements CommandExecutor {

    @Override
    public boolean onCommand(
            @NotNull CommandSender commandSender,
            @NotNull Command command,
            @NotNull String s,
            String @NotNull [] strings) {
        if (commandSender instanceof Player player) {
            openNewPlayerGuideBook(player);
        }
        return false;
    }

    private static @Nullable ItemStack guideBook() {
        GuideBookConfigParser guideBookConfigParser = new GuideBookConfigParser(OrzMC.plugin());
        GuideBookConfig guideBookConfig = guideBookConfigParser.parseConfig();
        if (guideBookConfig == null || !guideBookConfig.enable()) {
            return null;
        }

        ItemStack guideBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) guideBook.getItemMeta();
        bookMeta.setTitle(guideBookConfig.title());
        bookMeta.setAuthor(guideBookConfig.author());
        bookMeta.setGeneration(BookMeta.Generation.COPY_OF_COPY);

        ArrayList<TextComponent> bookPageComponents = new ArrayList<>();
        TextComponent.Builder guideBookPageBuilder = Component.text();
        for (ContentItem item : guideBookConfig.content()) {
            TextComponent.Builder textComponentBuilder = Component.text();
            if (item.isText()) {
                TextContent textItem = item.getText();
                if (textItem.content().isEmpty()) {
                    continue;
                }
                textComponentBuilder.append(Component.text(textItem.content()));
            } else if (item.isLink()) {
                LinkContent linkItem = item.getLink();
                TextComponent.Builder linkTextBuilder = Component.text();
                if (linkItem.content().isEmpty()) {
                    continue;
                }
                linkTextBuilder.append(Component.text(linkItem.content()));
                if (!linkItem.url().isEmpty()) {
                    Style defaultLinkStyle = Style.style()
                            .color(TextColor.fromCSSHexString("#5555FF"))
                            .build();
                    linkTextBuilder.style(defaultLinkStyle);
                    linkTextBuilder.clickEvent(ClickEvent.openUrl(linkItem.url()));
                    linkTextBuilder.hoverEvent(HoverEvent.showText(Component.text(linkItem.hoverText())));
                }
                textComponentBuilder.append(linkTextBuilder.build());
            }
            if (item.getStyle() != null) {
                TextStyle style = item.getStyle();
                if (style.getBold()) {
                    textComponentBuilder.decorate(TextDecoration.BOLD);
                }
                if (style.getUnderlined()) {
                    textComponentBuilder.decorate(TextDecoration.UNDERLINED);
                }
                if (!style.getColor().isEmpty()) {
                    TextColor textColor = TextColor.fromCSSHexString(style.getColor());
                    textComponentBuilder.color(textColor);
                }
            }
            Collections.nCopies(item.getNewlineCount(), Component.newline()).forEach(textComponentBuilder::append);
            TextComponent textComponent = textComponentBuilder.build();
            guideBookPageBuilder.append(textComponent);
            if (item.getPageBreak()) {
                TextComponent pageContent = guideBookPageBuilder.build();
                bookPageComponents.add(pageContent);
                guideBookPageBuilder = Component.text();
            }
        }
        TextComponent lastPageContent = guideBookPageBuilder.build();
        if (!lastPageContent.children().isEmpty()) {
            bookPageComponents.add(lastPageContent);
        }
        bookMeta.addPages(bookPageComponents.toArray(new TextComponent[0]));
        guideBook.setItemMeta(bookMeta);
        return guideBook;
    }

    private void openNewPlayerGuideBook(Player player) {
        ItemStack guideBook = guideBook();
        if (guideBook == null) {
            player.sendMessage(OrzUtil.failureText("服主未配置新手指南"));
            return;
        }
        player.openBook(guideBook);
    }

    public static void giveNewPlayerAGuideBook(Player player) {
        UUID playerUUID = player.getPlayerProfile().getId();
        if (playerUUID == null) return;

        OfflinePlayer offlinePlayer = OrzMC.server().getOfflinePlayer(playerUUID);
        if (!offlinePlayer.hasPlayedBefore()) {
            ItemStack guideBook = guideBook();
            if (guideBook != null) {
                player.getInventory().addItem(guideBook);
                player.sendMessage(OrzUtil.successText("获得新手指南"));
            }
        }
    }
}
