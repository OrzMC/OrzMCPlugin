package com.jokerhub.paper.plugin.orzmc.infra.version;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@code orzmc-build.properties} 烘焙契约测试：守护「自更新版本比对依赖构建期发布串 + 稳定
 * 构建时间」这一接缝。若 Gradle generateBuildInfo 任务退化（缺文件/坏格式），这里立刻显形，
 * 避免 UpdateService 静默回退插件描述版本造成 -dev 误判。
 */
class BuildInfoTest {

    @Test
    void load_fromTestClasspath_present() {
        Optional<BuildInfo> info = BuildInfo.load(getClass().getClassLoader());

        assertTrue(info.isPresent(), "测试类路径应有 orzmc-build.properties（generateBuildInfo 产物）");
        String version = info.get().buildVersion();
        assertTrue(
                version.matches("\\d+\\.\\d+\\.\\d+(?:[-.][A-Za-z0-9.]+)?"),
                "版本须为发布串（如 1.0.24、1.0.24-dev 或 1.0.24-dev.360），实际: " + version);
        assertNotEquals(Instant.EPOCH, info.get().buildTime(), "构建时间应为真实提交时间，非 EPOCH 兜底");
        assertTrue(info.get().buildTime().isAfter(Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void load_missingResource_returnsEmpty() {
        try (URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            assertTrue(BuildInfo.load(empty).isEmpty(), "无该资源的类加载器应返回空（调用方回退插件描述版本）");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
