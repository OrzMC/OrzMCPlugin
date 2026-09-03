package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs;
    private final Map<String, File> configFiles;
    private final Set<String> dirtyConfigs = ConcurrentHashMap.newKeySet();
    private final Set<String> alwaysSaveConfigs = ConcurrentHashMap.newKeySet();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configs = new ConcurrentHashMap<>();
        this.configFiles = new ConcurrentHashMap<>();

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

    /** 配置对应的磁盘文件（未注册返回 {@code null}）。供 schema 升级做备份/损坏判定。 */
    public File configFile(String name) {
        return configFiles.get(name);
    }

    /**
     * 落盘配置文件。synchronized 串行化共享 FileConfiguration 的并发写：
     * global/region 线程并发 save 同一文件会交叠写损坏 YAML 或丢更新（PermissionStore.save 场景）。
     */
    public synchronized boolean saveConfig(String name) {
        if (!configs.containsKey(name) || !configFiles.containsKey(name)) {
            return false;
        }
        try {
            configs.get(name).save(configFiles.get(name));
            dirtyConfigs.remove(name);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("保存配置文件失败: " + name + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 在同步块内原子地「取配置→变更→落盘」，与其他并发写/重载互斥。
     *
     * <p>若调用方先 {@link #getConfig(String)} 拿到实例、在 get/set 间隙另一线程
     * {@link #reloadConfig(String)} 替换了实例，set 会写进已废弃对象而丢失——
     * 因此 set+save 必须整体放进本方法的同步块内。返回是否成功落盘。</p>
     */
    public synchronized boolean updateConfig(String name, Consumer<FileConfiguration> updater) {
        if (!configs.containsKey(name) || !configFiles.containsKey(name)) {
            return false;
        }
        updater.accept(configs.get(name));
        return saveConfig(name);
    }

    public synchronized boolean reloadConfig(String name) {
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
        return java.util.Set.copyOf(configs.keySet());
    }

    public void markDirty(String name) {
        dirtyConfigs.add(name);
    }

    public void markAlwaysSave(String name) {
        alwaysSaveConfigs.add(name);
    }

    public void saveDirtyConfigs() {
        for (String name : configs.keySet()) {
            if (dirtyConfigs.contains(name) || alwaysSaveConfigs.contains(name)) {
                saveConfig(name);
            }
        }
    }

    public java.io.File dataFolder() {
        return plugin.getDataFolder();
    }
}
