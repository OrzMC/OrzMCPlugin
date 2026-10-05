package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

public record BotConfig(String cmdPromptChar, String discordServerLink, String qqGroupId) {

    /**
     * 业务层 bot 参数（v12 起权威在 config.yml {@code bot:} 段，IM 通道无关）：
     * 读 {@code bot} 段键；段缺失 → 默认（前缀 {@code $}、联系方式 null）。空串视为显式清空。
     * easybot.yml 旧键已随 v12 迁移自动清理（{@code ConfigService.migrateBotParamsToConfig}），不再回退读取。
     */
    public static BotConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new BotConfig("$", null, null);
        }
        String cmdPromptChar = cfg.getString("cmd_prompt_char", "$");
        String discordServerLink = cfg.getString("discord_server_link");
        String qqGroupId = cfg.getString("qq_group_id");
        return new BotConfig(cmdPromptChar, discordServerLink, qqGroupId);
    }
}
