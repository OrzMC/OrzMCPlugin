package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs;
    private final Map<String, File> configFiles;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();
        this.configFiles = new HashMap<>();

        if (!plugin.getDataFolder().exists()) {
            boolean ret = plugin.getDataFolder().mkdirs();
            plugin.getLogger().info("创建插件数据文件夹" + (ret ? "成功" : "失败"));
        }
    }

    public boolean registerConfig(String name) {
        return registerConfig(name, name + ".yml");
    }

    public boolean registerConfig(String name, String fileName) {
        try {
            File configFile = new File(plugin.getDataFolder(), fileName);
            if (!configFile.exists()) {
                plugin.saveResource(fileName, false);
                plugin.getLogger().info("创建默认配置文件: " + fileName);
            }
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            configs.put(name, config);
            configFiles.put(name, configFile);
            plugin.getLogger().info("成功加载配置文件: " + fileName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("加载配置文件失败: " + fileName + " - " + e.getMessage());
            return false;
        }
    }

    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }

    public boolean saveConfig(String name) {
        if (!configs.containsKey(name) || !configFiles.containsKey(name)) {
            return false;
        }
        try {
            configs.get(name).save(configFiles.get(name));
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("保存配置文件失败: " + name + " - " + e.getMessage());
            return false;
        }
    }

    public boolean reloadConfig(String name) {
        if (!configFiles.containsKey(name)) {
            return false;
        }
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFiles.get(name));
            configs.put(name, config);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("重载配置文件失败: " + name + " - " + e.getMessage());
            return false;
        }
    }

    public boolean configExists(String name) {
        return configs.containsKey(name);
    }

    public java.util.Set<String> getConfigNames() {
        return configs.keySet();
    }
}
