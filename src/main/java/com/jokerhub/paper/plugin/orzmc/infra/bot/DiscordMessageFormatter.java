package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import java.util.List;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.utils.SplitUtil;

public class DiscordMessageFormatter implements MessageFormatter {
    private static final int DISCORD_TEXT_LIMIT = 2_000;
    private static final String CODE_BLOCK_PREFIX = "```\n";
    private static final String CODE_BLOCK_SUFFIX = "```";

    @Override
    public List<String> format(String message, MessageEnvelope.Format format) {
        if (format == MessageEnvelope.Format.CODE_BLOCK || format == MessageEnvelope.Format.DEFAULT) {
            return SplitUtil.split(
                            message,
                            DISCORD_TEXT_LIMIT - CODE_BLOCK_PREFIX.length() - CODE_BLOCK_SUFFIX.length(),
                            true,
                            SplitUtil.Strategy.NEWLINE,
                            SplitUtil.Strategy.ANYWHERE)
                    .stream()
                    .map(part -> CODE_BLOCK_PREFIX + part + CODE_BLOCK_SUFFIX)
                    .collect(Collectors.toList());
        }
        return SplitUtil.split(
                message, DISCORD_TEXT_LIMIT, true, SplitUtil.Strategy.NEWLINE, SplitUtil.Strategy.ANYWHERE);
    }
}
