package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;

public class OrzDiscordBot extends OrzBaseBot {
    private final ArrayList<String> toBeSendMessageWhenApiReady = new ArrayList<>();
    private final BotInboundHandler inboundHandler;
    private final MessageFormatter formatter;
    private final ThrottledLogger throttledLogger;
    private final ServerScheduler scheduler;
    private JDA api;
    private boolean isApiReady;

    public OrzDiscordBot(
            ServerAccess server,
            ServerLogger logger,
            ServerScheduler scheduler,
            ConfigService configService,
            BotInboundHandler inboundHandler,
            MessageFormatter formatter,
            ThrottledLogger throttledLogger) {
        super(server, logger, configService);
        this.scheduler = scheduler;
        this.inboundHandler = inboundHandler;
        this.formatter = formatter;
        this.throttledLogger = throttledLogger;
    }

    @Override
    public void send(MessageEnvelope envelope) {
        if (envelope == null) return;
        MessageEnvelope.Format format = envelope.format() == null ? MessageEnvelope.Format.DEFAULT : envelope.format();
        List<String> parts = formatter.format(envelope.message(), format);
        if (envelope.targetType() == MessageEnvelope.TargetType.CHANNEL) {
            String channelKey = envelope.channelKey();
            if (channelKey == null || channelKey.isEmpty()) {
                sendToDefaultChannel(parts);
            } else {
                sendToChannelParts(channelKey, parts);
            }
            return;
        }
        if (envelope.targetType() == MessageEnvelope.TargetType.PRIVATE) {
            sendPrivate(String.join("\n", parts));
            return;
        }
        sendToDefaultChannel(parts);
    }

    @Override
    public boolean isEnable() {
        return botConfig.getBoolean("enable_discord_bot");
    }

    @Override
    public void setup() {
        if (!this.isEnable()) {
            throttledLogger.info("discord", "Discord Bot Disabled!");
            return;
        }
        HealthRegistry.setEnabled("discord", true);
        String minecraftVersion = server.server().getMinecraftVersion();
        String serverInfo = "Minecraft" + "(" + minecraftVersion + ")";
        String botTokenBase64Encoded = botConfig.getString("discord_bot_token_base64_encoded");
        String botToken = new String(Base64.getDecoder().decode(botTokenBase64Encoded));
        try {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry.setLastError("discord", e.toString());
                throttledLogger.error("discord-thread", "Discord线程异常: " + e);
            });
            JDABuilder builder = JDABuilder.createLight(
                    botToken, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS);
            String proxyType = botConfig.getString("discord_proxy_type");
            String proxyHost = botConfig.getString("discord_proxy_host");
            int proxyPort = botConfig.getInt("discord_proxy_port");
            OkHttpClient.Builder http = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS);
            if (proxyType != null
                    && !"none".equalsIgnoreCase(proxyType)
                    && proxyHost != null
                    && !proxyHost.isEmpty()
                    && proxyPort > 0) {
                Proxy.Type type = "SOCKS".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
                http.proxy(new Proxy(type, new InetSocketAddress(proxyHost, proxyPort)));
            }
            try {
                builder.setHttpClient(http.build());
            } catch (Throwable ignored) {
            }
            api = builder.addEventListeners(new ListenerAdapter() {
                        @Override
                        public void onReady(@NotNull ReadyEvent event) {
                            try {
                                super.onReady(event);
                                isApiReady = true;
                                HealthRegistry.setApiReady("discord", true);
                                toBeSendMessageWhenApiReady.forEach(message -> sendPublic(message));
                                toBeSendMessageWhenApiReady.clear();
                            } catch (Exception e) {
                                HealthRegistry.setLastError("discord", e.toString());
                                throttledLogger.error("discord-onready", "Discord Ready 事件异常: " + e);
                            }
                        }

                        @Override
                        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
                            try {
                                super.onMessageReceived(event);
                                if (event.getAuthor().isBot()) return;
                                Member member = event.getMember();
                                if (member == null) return;
                                boolean isAdmin = member.hasPermission(Permission.MANAGE_SERVER)
                                        || member.hasPermission(Permission.ADMINISTRATOR)
                                        || member.hasPermission(Permission.MANAGE_CHANNEL);
                                String content = event.getMessage().getContentRaw();
                                BotInboundDispatcher.dispatch(inboundHandler, content, isAdmin, env -> {
                                    MessageChannel channel = event.getChannel();
                                    MessageEnvelope.Format format =
                                            env.format() == null ? MessageEnvelope.Format.DEFAULT : env.format();
                                    formatter.format(env.message(), format).forEach(part -> channel.sendMessage(part)
                                            .queue());
                                });
                            } catch (Exception e) {
                                HealthRegistry.setLastError("discord", e.toString());
                                throttledLogger.error("discord-onmessage", "Discord 消息事件异常: " + e);
                            }
                        }
                    })
                    .setActivity(Activity.playing(serverInfo))
                    .build();

            int graceSeconds = botConfig.getInt("discord_connect_grace_seconds");
            boolean autoDisable = botConfig.getBoolean("discord_auto_disable_on_connect_error");
            if (autoDisable) {
                int ticks = (graceSeconds <= 0 ? 10 : graceSeconds) * 20;
                scheduler.runLater(
                        () -> {
                            if (!isApiReady) {
                                try {
                                    api.shutdown();
                                } catch (Exception ignored) {
                                }
                                HealthRegistry.setEnabled("discord", false);
                                throttledLogger.warning("discord-disable", "Discord不可达，已自动禁用机器人");
                            }
                        },
                        ticks);
            }
        } catch (Exception e) {
            HealthRegistry.setLastError("discord", e.toString());
            throttledLogger.error("discord-init", "Discord初始化异常: " + e);
        }
    }

    @Override
    public void teardown() {
        try {
            isApiReady = false;
            if (api != null) {
                api.shutdown();
                try {
                    api.awaitShutdown();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        } finally {
            api = null;
        }
    }

    @Override
    protected void sendPublic(String message) {
        if (!this.isEnable()) {
            return;
        }
        if (!isApiReady) {
            toBeSendMessageWhenApiReady.add(message);
            return;
        }
        try {
            String playerTextChannelId = botConfig.getString("discord_player_text_channel_id");
            TextChannel channel = playerTextChannelId != null ? api.getTextChannelById(playerTextChannelId) : null;
            if (channel == null) {
                logger.logger().warning("your discord bot not in this text channel: " + playerTextChannelId);
                return;
            }
            formatter.format(message, MessageEnvelope.Format.DEFAULT).forEach(part -> channel.sendMessage(part)
                    .queue());
        } catch (Exception e) {
            HealthRegistry.setLastError("discord", e.toString());
            throttledLogger.error("discord-send", "Discord消息发送异常: " + e);
        }
    }

    @Override
    protected void sendPrivate(String message) {}

    @Override
    protected void sendChannel(String channelKey, String message) {
        if (!this.isEnable()) return;
        if (!isApiReady) {
            toBeSendMessageWhenApiReady.add(message);
            return;
        }
        try {
            sendToChannelParts(channelKey, formatter.format(message, MessageEnvelope.Format.DEFAULT));
        } catch (Exception e) {
            com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry.setLastError("discord", e.toString());
            throttledLogger.error("discord-send", "Discord指定频道发送异常: " + e);
        }
    }

    private void sendToDefaultChannel(List<String> parts) {
        if (!this.isEnable()) {
            return;
        }
        if (!isApiReady) {
            toBeSendMessageWhenApiReady.add(String.join("\n", parts));
            return;
        }
        String playerTextChannelId = botConfig.getString("discord_player_text_channel_id");
        TextChannel channel = playerTextChannelId != null ? api.getTextChannelById(playerTextChannelId) : null;
        if (channel == null) {
            logger.logger().warning("your discord bot not in this text channel: " + playerTextChannelId);
            return;
        }
        parts.forEach(part -> channel.sendMessage(part).queue());
    }

    private void sendToChannelParts(String channelKey, List<String> parts) {
        if (!this.isEnable()) return;
        if (!isApiReady) {
            toBeSendMessageWhenApiReady.add(String.join("\n", parts));
            return;
        }
        String id = botConfig.getString("channels." + channelKey + ".discord");
        TextChannel channel = id != null ? api.getTextChannelById(id) : null;
        if (channel == null) {
            sendToDefaultChannel(parts);
            return;
        }
        parts.forEach(part -> channel.sendMessage(part).queue());
    }
}
