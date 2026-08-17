package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * 捕获 Notifier 事件与服务端消息的测试替身（NotifierSink 策略，见 CLAUDE.md「通知策略」）。
 * 注册到 {@code notifier().registerSink(...)} 后，即可断言插件发出的每条通知消息。
 */
final class CapturingSink implements NotifierSink {

    /** 包内测试直接读取捕获结果。 */
    final List<String> keys = new ArrayList<>();

    final List<MessageEnvelope> envelopes = new ArrayList<>();
    final List<Component> serverMessages = new ArrayList<>();

    @Override
    public void server(Component message) {
        serverMessages.add(message);
    }

    @Override
    public void event(String key, MessageEnvelope envelope) {
        keys.add(key);
        envelopes.add(envelope);
    }

    void clear() {
        keys.clear();
        envelopes.clear();
        serverMessages.clear();
    }

    boolean isEmpty() {
        return keys.isEmpty();
    }

    MessageEnvelope lastEnvelope() {
        return envelopes.get(envelopes.size() - 1);
    }
}
