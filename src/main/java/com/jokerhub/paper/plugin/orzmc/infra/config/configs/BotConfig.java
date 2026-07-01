package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

public record BotConfig(String cmdPromptChar, String discordServerLink, String qqGroupId) {

    public static BotConfig from(ConfigurationSection cfg) {
        String cmdPromptChar = cfg.getString("cmd_prompt_char", "$");
        String discordServerLink = cfg.getString("discord_server_link");
        String qqGroupId = cfg.getString("qq_group_id");
        return new BotConfig(cmdPromptChar, discordServerLink, qqGroupId);
    }
}
