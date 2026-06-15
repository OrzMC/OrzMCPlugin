package com.jokerhub.paper.plugin.orzmc.testutil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 测试资源加载工具。
 *
 * <p>从 {@code src/test/resources/} 加载 YAML 文件为 Bukkit {@link FileConfiguration}，
 * 供配置解析测试使用。</p>
 */
public final class TestResourceHelper {

    private TestResourceHelper() {}

    /**
     * 从测试资源目录加载 YAML 配置。
     *
     * @param resourceName 资源文件名（如 "config-test.yml"）
     * @return 解析后的 {@link FileConfiguration}，资源不存在时返回空配置
     */
    public static FileConfiguration loadYaml(String resourceName) {
        InputStream in = TestResourceHelper.class.getClassLoader().getResourceAsStream(resourceName);
        if (in == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /**
     * 从测试资源目录加载 YAML 配置，资源不存在时抛出异常。
     *
     * @param resourceName 资源文件名
     * @return 解析后的 {@link FileConfiguration}
     * @throws NullPointerException 资源不存在
     */
    public static FileConfiguration loadYamlOrFail(String resourceName) {
        InputStream in = Objects.requireNonNull(
                TestResourceHelper.class.getClassLoader().getResourceAsStream(resourceName),
                "测试资源 " + resourceName + " 不存在");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /**
     * 从文件路径加载 YAML 配置。
     *
     * @param file YAML 文件
     * @return 解析后的 {@link FileConfiguration}
     */
    public static FileConfiguration loadYaml(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }
}
