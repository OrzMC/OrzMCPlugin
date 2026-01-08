package com.jokerhub.paper.plugin.orzmc;

import com.jokerhub.paper.plugin.orzmc.commands.OrzBotStatus;
import com.jokerhub.paper.plugin.orzmc.commands.OrzGuideBook;
import com.jokerhub.paper.plugin.orzmc.commands.OrzMenuCommand;
import com.jokerhub.paper.plugin.orzmc.commands.OrzTPBow;
import com.jokerhub.paper.plugin.orzmc.events.*;
import com.jokerhub.paper.plugin.orzmc.utils.bot.OrzBotManager;
import com.jokerhub.paper.plugin.orzmc.utils.config.AdvancedConfigManager;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;

public final class OrzMC extends JavaPlugin implements Listener {
    public AdvancedConfigManager configManager;
    private OrzBotManager botManager;

    @Override
    public void onEnable() {
        getLogger().info("OrzMC 插件生效!");
        setupConfigManager();
        setupEventListener();
        setupCommandHandler();
        setupBotManager();
        setupServerForceWhitelist();
    }

    @Override
    public void onDisable() {
        getLogger().info("OrzMC 插件失效!");
        tearDownBotManager();
        tearDownConfigManager();
        notifyServerStop();
    }

    // 公共静态成员
    public static OrzMC plugin() {
        return JavaPlugin.getPlugin(OrzMC.class);
    }

    public static Server server() {
        return plugin().getServer();
    }

    public static Logger logger() {
        return OrzMC.plugin().getLogger();
    }

    // 公共方法
    public void sendPublicMessage(String message) {
        OrzMC.debugInfo(message);
        botManager.sendMessage(message, false);
    }

    public void sendPrivateMessage(String message) {
        debugInfo(message);
        botManager.sendMessage(message, true);
    }

    public static void debugInfo(String msg) {
        if (!OrzDebugEvent.debug) {
            return;
        }
        OrzMC.logger().info(msg);
    }

    private void notifyServerStop() {
        String minecraftVersion = getServer().getMinecraftVersion();
        String stringBuilder = "Minecraft " + minecraftVersion + "\n" + "------" + "\n" + "服务停止" + "\n\n" + "停止状态无法响应命令消息";
        sendPublicMessage(stringBuilder);
    }

    private void setupServerForceWhitelist() {
        boolean forceWhitelist = configManager.getConfig("config").getBoolean("force_whitelist");
        getServer().setWhitelist(forceWhitelist);
        getServer().setWhitelistEnforced(forceWhitelist);
        getServer().reloadWhitelist();
        getServer().setDefaultGameMode(GameMode.SURVIVAL);
        if (forceWhitelist) {
            getLogger().info("服务端使用强制白名单机制");
        }
    }

    private void setupEventListener() {
        Listener[] eventListeners = new Listener[]{new OrzBowShootEvent(), new OrzPlayerEvent(this), new OrzTPEvent(), new OrzTNTEvent(this), new OrzMenuEvent(), new OrzServerEvent(this), new OrzWhiteListEvent(this), new OrzDebugEvent()};
        Arrays.stream(eventListeners).forEach(eventListener -> getServer().getPluginManager().registerEvents(eventListener, this));
    }

    private void setupCommandHandler() {
        Map<String, CommandExecutor> commandHandlers = Map.of("tpbow", new OrzTPBow(), "guide", new OrzGuideBook(), "menu", new OrzMenuCommand(), "botstatus", new OrzBotStatus());
        commandHandlers.forEach((key, value) -> {
            PluginCommand cmd = getCommand(key);
            if (cmd != null) {
                cmd.setExecutor(value);
            }
        });
    }

    private void setupBotManager() {
        botManager = new OrzBotManager(this);
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                botManager.setup();
            } catch (Exception e) {
                getLogger().severe("BotManager 初始化失败: " + e.getMessage());
            }
        });
    }

    private void tearDownBotManager() {
        botManager.tearDown();
    }

    private void setupConfigManager() {
        configManager = new AdvancedConfigManager(this);
        configManager.registerConfig("config");
        configManager.setDefaults("config", config -> {
            // 配置默认值
        });
        configManager.registerConfig("bot");
        configManager.setDefaults("bot", config -> {
            // 配置默认值
        });
        configManager.registerConfig("guide_book");
        configManager.setDefaults("guide_book", config -> {
            // 配置默认值
        });
        configManager.registerConfig("tnt");
        configManager.setDefaults("tnt", config -> {
            // 配置默认值
        });
    }

    private void tearDownConfigManager() {
        for (String configName : configManager.getConfigNames()) {
            configManager.saveConfig(configName);
        }
    }
}
