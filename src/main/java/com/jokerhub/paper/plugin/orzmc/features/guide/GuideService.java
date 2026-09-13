package com.jokerhub.paper.plugin.orzmc.features.guide;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.GuideBookConfigParser;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.GuideBookRenderer;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuideBookConfig;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.server.OrzUtil;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 新手指南书：首次进服发放 + {@code /guide} 打开。
 *
 * <p>配置解析结果**进程内缓存**（{@link #invalidate()} 由 {@code /orzmc config reload} 触发失效），
 * 取代旧实现「每次发放/每次打开都重新读盘 + 全量 YAML 解析」的路径；解析告警与渲染告警统一走插件日志。</p>
 */
public final class GuideService {
    private final ServerFacade server;
    private final ConfigService configService;
    private final GuideBookConfigParser parser;
    private final GuideBookRenderer renderer;
    private final OrzTextStyles styles;
    private final I18nService i18n;

    private final Object cacheLock = new Object();
    private volatile GuideBookConfig cachedConfig;

    public GuideService(ServerFacade server, ConfigService configService, OrzTextStyles styles, I18nService i18n) {
        this.server = server;
        this.configService = configService;
        this.styles = styles;
        this.i18n = i18n;
        this.parser = new GuideBookConfigParser();
        this.renderer =
                new GuideBookRenderer(warning -> server.plugin().getLogger().warning(warning));
    }

    /** 解析并缓存 guide_book.yml；配置改动后由 {@code /orzmc config reload} 调用。 */
    public void reload() {
        synchronized (cacheLock) {
            cachedConfig = load();
        }
    }

    /** 丢弃缓存，下次使用时重新解析（热重载回调入口）。 */
    public void invalidate() {
        cachedConfig = null;
    }

    /** 构建成书；未启用或无内容时返回 {@code null}（调用方提示「服主未配置新手指南」）。 */
    public ItemStack buildGuideBook() {
        GuideBookConfig config = currentConfig();
        if (config == null || !config.enable() || !config.hasContent()) {
            return null;
        }
        return renderer.render(config);
    }

    public void openGuide(Player player) {
        ItemStack guideBook = buildGuideBook();
        if (guideBook == null) {
            player.sendMessage(
                    OrzUtil.failureText(styles, i18n.msg(i18n.langFor(player), MessageKeys.GUIDE_NOT_CONFIGURED)));
            return;
        }
        player.openBook(guideBook);
    }

    public void giveIfFirstJoin(Player player) {
        UUID playerUUID = player.getPlayerProfile().getId();
        if (playerUUID == null) return;
        OfflinePlayer offlinePlayer = server.server().getOfflinePlayer(playerUUID);
        if (!offlinePlayer.hasPlayedBefore()) {
            ItemStack guideBook = buildGuideBook();
            if (guideBook != null) {
                player.getInventory().addItem(guideBook);
                player.sendMessage(OrzUtil.successText(styles, i18n.msg(i18n.langFor(player), MessageKeys.GUIDE_GOT)));
            }
        }
    }

    private GuideBookConfig currentConfig() {
        GuideBookConfig snapshot = cachedConfig;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (cacheLock) {
            if (cachedConfig == null) {
                cachedConfig = load();
            }
            return cachedConfig;
        }
    }

    private GuideBookConfig load() {
        GuideBookConfigParser.ParseResult result = parser.parse(configService.getConfig("guide_book"));
        for (String issue : result.issues()) {
            server.plugin().getLogger().warning("guide_book.yml: " + issue);
        }
        return result.config();
    }
}
