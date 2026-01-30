package com.jokerhub.paper.plugin.orzmc.infra.notify;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;

public final class Notifier {
    private Notifier() {}

    private static NotifierSink sink = new DefaultSink();

    public static void server(Component message) {
        sink.server(message);
    }

    public static void bot(String message) {
        sink.botPublic(message);
    }

    public static void botPrivate(String message) {
        sink.botPrivate(message);
    }

    public static void event(String key, String message) {
        sink.event(key, message);
    }

    public static void registerSink(NotifierSink s) {
        sink = s == null ? sink : s;
    }

    private static final class DefaultSink implements NotifierSink {
        @Override
        public void server(Component message) {
            OrzMC.server().sendMessage(message);
        }

        @Override
        public void botPublic(String message) {
            OrzMC.plugin().sendPublicMessage(message);
        }

        @Override
        public void botPrivate(String message) {
            OrzMC.plugin().sendPrivateMessage(message);
        }

        @Override
        public void botChannel(String channelKey, String message) {
            OrzMC.plugin().sendToChannel(channelKey, message);
        }

        @Override
        public void event(String key, String message) {
            FileConfiguration cfg = OrzMC.plugin().configManager.getConfig("notifications");
            TypedConfigs.Notifications ns = TypedConfigs.Notifications.from(cfg);
            TypedConfigs.NotifyPolicy p =
                    ns.policies().getOrDefault(key, new TypedConfigs.NotifyPolicy(false, true, true));
            if (p.publicEnabled()) {
                botPublic(message);
            }
            if (p.privateEnabled()) {
                botPrivate(message);
            }
            Object raw = OrzMC.plugin()
                    .configManager
                    .getConfig("notifications")
                    .get("notifications." + key + ".channel_key");
            String channelKey = raw == null ? null : String.valueOf(raw);
            if (channelKey != null && !channelKey.isEmpty()) {
                botChannel(channelKey, message);
            }
        }
    }
}
