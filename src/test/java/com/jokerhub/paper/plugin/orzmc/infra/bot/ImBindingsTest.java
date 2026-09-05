package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** ImBindings 读取层单测：会话归一（平台前缀补全）、启用判定（任一目标非空）、未绑定 fail-closed。 */
class ImBindingsTest {

    private static YamlConfiguration bindings(String platform, String admin, String player, String dm) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sessions." + platform + ".admin_group", admin);
        yaml.set("sessions." + platform + ".player_group", player);
        yaml.set("sessions." + platform + ".admin_dm", dm);
        return yaml;
    }

    @Test
    void values_normalizedWithPlatformPrefix() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sessions.qq.admin_group", "group:G-1");
        yaml.set("sessions.qq.player_group", "group:G-2");
        yaml.set("sessions.qq.admin_dm", "user:U-1");

        ImConversation conv = ImBindings.from(yaml).conversation("qq");
        assertTrue(conv.enabled());
        assertEquals("qq:group:G-1", conv.adminGroup());
        assertEquals("qq:group:G-2", conv.playerGroup());
        assertEquals("qq:user:U-1", conv.adminDm());
        assertEquals("qq:group:G-2", conv.publicTarget(), "PUBLIC 优先 player_group");
    }

    @Test
    void alreadyPrefixedValue_keptAsIs() {
        YamlConfiguration yaml = bindings("qq", "qq:group:G-9", "", "");
        assertEquals("qq:group:G-9", ImBindings.from(yaml).conversation("qq").adminGroup());
    }

    @Test
    void allBlank_notEnabled_failClosed() {
        ImConversation conv = ImBindings.from(bindings("qq", "", "", "")).conversation("qq");
        assertFalse(conv.enabled(), "全空 = 未绑定，入站门槛拒绝");
    }

    @Test
    void missingSection_returnsDisabledConversation() {
        ImConversation conv = ImBindings.from(new YamlConfiguration()).conversation("qq");
        assertFalse(conv.enabled());
    }

    @Test
    void emptyPlayer_fallsBackToAdminForPublic() {
        ImConversation conv =
                ImBindings.from(bindings("qq", "group:G-1", "", "")).conversation("qq");
        assertTrue(conv.enabled(), "仅管理群也算已绑定（入站/降级目标）");
        assertEquals("qq:group:G-1", conv.publicTarget(), "player 空降级 admin");
    }

    @Test
    void conversations_returnsBoundInFileOrder() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sessions.feishu.admin_group", "chat:fc-1");
        yaml.set("sessions.qq.admin_group", "group:G-1");
        yaml.set("sessions.qq.player_group", "");

        List<ImConversation> all = ImBindings.from(yaml).conversations();
        assertEquals(2, all.size());
        assertEquals("feishu:chat:fc-1", all.get(0).adminGroup());
        assertEquals("qq:group:G-1", all.get(1).adminGroup());
    }
}
