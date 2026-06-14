package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

public record BotConfig(String cmdPromptChar, String discordServerLink, String qqGroupId, String qqPlayerGroupId) {

    public static BotConfig from(ConfigurationSection cfg) {
        String cmdPromptChar = cfg.getString("cmd_prompt_char", "$");
        String discordServerLink = cfg.getString("discord_server_link");
        String qqGroupId = cfg.getString("qq_group_id");
        String qqPlayerGroupId = cfg.getString("qq_player_group_id", qqGroupId);
        return new BotConfig(cmdPromptChar, discordServerLink, qqGroupId, qqPlayerGroupId);
    }
}
