package com.jokerhub.paper.plugin.orzmc.utils.bot;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.utils.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.utils.OrzMessageParser;
import com.jokerhub.paper.plugin.orzmc.utils.ThrottledLogger;
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
import net.dv8tion.jda.api.utils.SplitUtil;
import org.jetbrains.annotations.NotNull;
import okhttp3.OkHttpClient;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class OrzDiscordBot extends OrzBaseBot {

    private final ArrayList<String> toBeSendMessageWhenApiReady = new ArrayList<>();
    private final String codeBlockPrefix = "```\n";
    private final String codeBlockSuffix = "```";
    private JDA api;
    private boolean isApiReady;

    public OrzDiscordBot(OrzMC plugin) {
        super(plugin);
    }

    private List<String> codeBlockSplitMessage(String rawMessage) {
        int discordTextLengthLimit = 2_000;
        return SplitUtil.split(rawMessage, discordTextLengthLimit - codeBlockPrefix.length() - codeBlockSuffix.length(), true, SplitUtil.Strategy.NEWLINE, SplitUtil.Strategy.ANYWHERE).stream().map(part -> codeBlockPrefix + part + codeBlockSuffix).collect(Collectors.toList());

    }

    @Override
    public boolean isEnable() {
        return botConfig.getBoolean("enable_discord_bot");
    }

    @Override
    public void setup() {
        if (!this.isEnable()) {
            OrzMC.debugInfo("Discord Bot Disabled!");
            return;
        }
        HealthRegistry.setEnabled("discord", true);
        String minecraftVersion = OrzMC.server().getMinecraftVersion();
        String serverInfo = "Minecraft" + "(" + minecraftVersion + ")";
        String botTokenBase64Encoded = botConfig.getString("discord_bot_token_base64_encoded");
        String botToken = new String(Base64.getDecoder().decode(botTokenBase64Encoded));
        try {
            long throttleMs = botConfig.getLong("log_throttle_ms");
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                HealthRegistry.setLastError("discord", e.toString());
                ThrottledLogger.error("discord-thread", "Discord线程异常: " + e, throttleMs <= 0 ? 5000 : throttleMs);
            });
            JDABuilder builder = JDABuilder.createLight(botToken, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS);
            String proxyType = botConfig.getString("discord_proxy_type");
            String proxyHost = botConfig.getString("discord_proxy_host");
            int proxyPort = botConfig.getInt("discord_proxy_port");
            OkHttpClient.Builder http = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS);
            if (proxyType != null && !"none".equalsIgnoreCase(proxyType) && proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0) {
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
                        toBeSendMessageWhenApiReady.forEach(message -> sendMessage(message));
                        toBeSendMessageWhenApiReady.clear();
                    } catch (Exception e) {
                        HealthRegistry.setLastError("discord", e.toString());
                        ThrottledLogger.error("discord-onready", "Discord Ready 事件异常: " + e, throttleMs <= 0 ? 5000 : throttleMs);
                    }
                }

                @Override
                public void onMessageReceived(@NotNull MessageReceivedEvent event) {
                    try {
                        super.onMessageReceived(event);
                        if (event.getAuthor().isBot()) return;
                        Member member = event.getMember();
                        if (member == null) return;
                        boolean isAdmin = member.hasPermission(Permission.MANAGE_SERVER) || member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_CHANNEL);
                        String content = event.getMessage().getContentRaw();
                        OrzMessageParser.parse(content, isAdmin, info -> {
                            if (info != null) {
                                MessageChannel channel = event.getChannel();
                                codeBlockSplitMessage(info).forEach(part -> channel.sendMessage(part).queue());
                            }
                        });
                    } catch (Exception e) {
                        HealthRegistry.setLastError("discord", e.toString());
                        ThrottledLogger.error("discord-onmessage", "Discord 消息事件异常: " + e, throttleMs <= 0 ? 5000 : throttleMs);
                    }
                }
            }).setActivity(Activity.playing(serverInfo)).build();

            int graceSeconds = botConfig.getInt("discord_connect_grace_seconds");
            boolean autoDisable = botConfig.getBoolean("discord_auto_disable_on_connect_error");
            if (autoDisable) {
                int ticks = (graceSeconds <= 0 ? 10 : graceSeconds) * 20;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!isApiReady) {
                        try {
                            api.shutdown();
                        } catch (Exception ignored) { }
                        HealthRegistry.setEnabled("discord", false);
                        ThrottledLogger.warning("discord-disable", "Discord不可达，已自动禁用机器人", throttleMs <= 0 ? 5000 : throttleMs);
                    }
                }, ticks);
            }
        } catch (Exception e) {
            HealthRegistry.setLastError("discord", e.toString());
            ThrottledLogger.error("discord-init", "Discord初始化异常: " + e, botConfig.getLong("log_throttle_ms"));
        }
    }

    @Override
    public void teardown() {
        if (this.isEnable()) {
            api.shutdown();
        }
    }

    public void sendMessage(String message) {
        if (!this.isEnable()) {
            OrzMC.debugInfo("Discord Bot Disabled!");
            return;
        }
        if (!isApiReady) {
            toBeSendMessageWhenApiReady.add(message);
            return;
        }
        try {
            long throttleMs = botConfig.getLong("log_throttle_ms");
            TextChannel channel;
            String playerTextChannelId = botConfig.getString("discord_player_text_channel_id");
            if (playerTextChannelId != null) {
                channel = api.getTextChannelById(playerTextChannelId);
            } else {
                channel = null;
            }
            if (channel != null) {
                codeBlockSplitMessage(message).forEach(part -> channel.sendMessage(part).queue());
            } else {
                OrzMC.logger().warning("your discord bot not in this text channel: " + playerTextChannelId);
            }
        } catch (Exception e) {
            HealthRegistry.setLastError("discord", e.toString());
            ThrottledLogger.error("discord-send", "Discord消息发送异常: " + e, botConfig.getLong("log_throttle_ms"));
        }
    }

    @Override
    public void sendPrivateMessage(String message) {

    }
}
