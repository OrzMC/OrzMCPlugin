package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigWrapper {
    private final FileConfiguration config;
    private final String pathPrefix;

    public ConfigWrapper(FileConfiguration config) {
        this(config, "");
    }

    public ConfigWrapper(FileConfiguration config, String pathPrefix) {
        this.config = config;
        this.pathPrefix = pathPrefix.isEmpty() ? "" : pathPrefix + ".";
    }

    public String getString(String path) {
        return config.getString(pathPrefix + path);
    }

    public String getString(String path, String defaultValue) {
        return config.getString(pathPrefix + path, defaultValue);
    }

    public int getInt(String path) {
        return config.getInt(pathPrefix + path);
    }

    public int getInt(String path, int defaultValue) {
        return config.getInt(pathPrefix + path, defaultValue);
    }

    public double getDouble(String path) {
        return config.getDouble(pathPrefix + path);
    }

    public double getDouble(String path, double defaultValue) {
        return config.getDouble(pathPrefix + path, defaultValue);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(pathPrefix + path);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(pathPrefix + path, defaultValue);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(pathPrefix + path);
    }

    public <T> T getObject(String path, Class<T> clazz) {
        return clazz.cast(config.get(pathPrefix + path));
    }

    public void set(String path, Object value) {
        config.set(pathPrefix + path, value);
    }

    public boolean contains(String path) {
        return config.contains(pathPrefix + path);
    }

    public ConfigWrapper getSection(String path) {
        return new ConfigWrapper(config, pathPrefix + path);
    }
}
