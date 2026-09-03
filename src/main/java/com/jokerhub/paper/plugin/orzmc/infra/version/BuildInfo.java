package com.jokerhub.paper.plugin.orzmc.infra.version;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

/**
 * 运行时读取构建期烘焙的版本信息（{@code orzmc-build.properties}，见 build.gradle.kts
 * {@code generateBuildInfo}）。插件自更新用它精确比对「当前运行版本」与发布通道最新版，
 * 避免拿 paper-plugin.yml 的基础版本号（如 1.0.24）去和 {@code -dev.N} 构建误判。
 *
 * @param buildVersion 发布版本串（与 Hangar/Modrinth 版本名一致，如 1.0.24-dev.360）
 * @param buildTime 构建时间（取 HEAD 提交时间，稳定且可用于新旧比较）
 */
public record BuildInfo(String buildVersion, Instant buildTime) {

    private static final String RESOURCE = "orzmc-build.properties";

    /** 从给定类加载器读取构建信息；资源缺失/损坏时返回空（调用方回退插件描述版本）。 */
    public static Optional<BuildInfo> load(ClassLoader classLoader) {
        try (InputStream in = classLoader.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Optional.empty();
            }
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("buildVersion");
            String time = props.getProperty("buildTime");
            if (version == null || version.isBlank() || time == null || time.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new BuildInfo(version.trim(), Instant.parse(time.trim())));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
