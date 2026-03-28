package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import java.util.List;

public class PlainMessageFormatter implements MessageFormatter {
    @Override
    public List<String> format(String message, MessageEnvelope.Format format) {
        if (message == null) {
            return List.of();
        }
        return List.of(message);
    }
}
