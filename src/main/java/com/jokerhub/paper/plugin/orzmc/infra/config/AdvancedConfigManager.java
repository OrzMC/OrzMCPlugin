package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.util.function.Consumer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class AdvancedConfigManager extends ConfigManager {
    private final JavaPlugin plugin;

    public AdvancedConfigManager(JavaPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    public ConfigWrapper getWrapper(String configName) {
        return new ConfigWrapper(getConfig(configName));
    }

    public ConfigWrapper getWrapper(String configName, String pathPrefix) {
        return new ConfigWrapper(getConfig(configName), pathPrefix);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrSetDefault(String configName, String path, T defaultValue) {
        FileConfiguration config = getConfig(configName);
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            saveConfig(configName);
            plugin.getLogger().info("为配置 " + configName + " 设置默认值: " + path + " = " + defaultValue);
        }
        return (T) config.get(path);
    }

    public void setDefaults(String configName, Consumer<FileConfiguration> defaultsSetter) {
        FileConfiguration config = getConfig(configName);
        defaultsSetter.accept(config);
        saveConfig(configName);
    }
}
