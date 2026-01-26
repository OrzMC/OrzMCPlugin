package com.jokerhub.paper.plugin.orzmc.utils.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs;
    private final Map<String, File> configFiles;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();
        this.configFiles = new HashMap<>();

        // 创建插件数据文件夹
        if (!plugin.getDataFolder().exists()) {
            boolean ret = plugin.getDataFolder().mkdirs();
            plugin.getLogger().info("创建插件数据文件夹" + (ret ? "成功" : "失败"));
        }
    }

    /**
     * 注册配置文件
     *
     * @param name 配置文件名（不含.yml后缀）
     * @return 是否成功注册
     */
    public boolean registerConfig(String name) {
        return registerConfig(name, name + ".yml");
    }

    /**
     * 注册配置文件
     *
     * @param name     配置标识名
     * @param fileName 实际文件名
     * @return 是否成功注册
     */
    public boolean registerConfig(String name, String fileName) {
        try {
            File configFile = new File(plugin.getDataFolder(), fileName);

            // 如果文件不存在，从jar中复制默认配置
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

    /**
     * 获取配置
     */
    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }

    /**
     * 保存配置
     */
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

    /**
     * 重载配置
     */
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

    /**
     * 检查配置是否存在
     */
    public boolean configExists(String name) {
        return configs.containsKey(name);
    }

    /**
     * 获取所有已注册的配置名称
     */
    public java.util.Set<String> getConfigNames() {
        return configs.keySet();
    }
}
