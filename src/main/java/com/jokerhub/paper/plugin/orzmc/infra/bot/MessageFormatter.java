package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import java.util.List;

public interface MessageFormatter {
    List<String> format(String message, MessageEnvelope.Format format);
}
