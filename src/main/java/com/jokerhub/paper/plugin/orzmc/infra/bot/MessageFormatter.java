package com.jokerhub.paper.plugin.orzmc.infra.bot;

import java.util.List;

public interface MessageFormatter {
    List<String> format(String message, MessageEnvelope.Format format);
}
