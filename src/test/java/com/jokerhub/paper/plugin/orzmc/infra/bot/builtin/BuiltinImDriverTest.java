package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.QqPlatformConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * BuiltinImDriver 单测（注入替身平台工厂，不触发真实网络）：启用 reconcile、凭据不可用停用、
 * 出站按 im_bindings 路由到对应平台、未绑定无目标跳过、reload 停旧建新、tearDown 清理。
 */
class BuiltinImDriverTest {

    private final ConfigService configService = Mockito.mock(ConfigService.class);
    private final MessageFormatter formatter = (message, format) -> List.of(message);

    /** 替身平台：记录生命周期与投递。 */
    static final class FakePlatform implements BuiltinPlatform {
        private final String platformName;
        final List<String> sent = new CopyOnWriteArrayList<>();
        volatile boolean started;
        volatile boolean stopped;

        FakePlatform() {
            this("qq");
        }

        FakePlatform(String platformName) {
            this.platformName = platformName;
        }

        @Override
        public String platform() {
            return platformName;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public void reconnectIfNeeded() {
            started = true; // 简化：重启标记
        }

        @Override
        public void send(String target, String text) {
            sent.add(target + "|" + text);
        }
    }

    private void imConfig(QqPlatformConfig qq) {
        YamlConfiguration im = new YamlConfiguration();
        im.set("backend", "builtin");
        if (qq != null) {
            im.set("platforms.qq.enabled", qq.enabled());
            im.set("platforms.qq.app_id", qq.appId());
            im.set("platforms.qq.client_secret", qq.clientSecret());
        }
        Mockito.when(configService.getConfig("im")).thenReturn(im);
    }

    private void bindings(String adminGroup, String playerGroup, String adminDm) {
        YamlConfiguration b = new YamlConfiguration();
        b.set("sessions.qq.admin_group", adminGroup);
        b.set("sessions.qq.player_group", playerGroup);
        b.set("sessions.qq.admin_dm", adminDm);
        Mockito.when(configService.getConfig("im_bindings")).thenReturn(b);
    }

    private BuiltinImDriver driver(FakePlatform platform) {
        return new BuiltinImDriver(silentLogger(), configService, formatter, cfg -> platform);
    }

    private static ServerLogger silentLogger() {
        Logger raw = Logger.getLogger("builtin-im-driver-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return () -> raw;
    }

    // =====================================================================
    // 用例
    // =====================================================================

    @Test
    void setup_withUsableQq_startsPlatform() {
        FakePlatform platform = new FakePlatform();
        imConfig(new QqPlatformConfig(true, "app-1", "secret-1"));
        bindings("", "", "");

        BuiltinImDriver driver = driver(platform);
        driver.setup();

        assertTrue(platform.started, "可用平台应被启动");
    }

    @Test
    void reload_whenPlatformUnusable_stopsAndDoesNotStart() {
        FakePlatform platform = new FakePlatform();
        imConfig(new QqPlatformConfig(false, "", ""));
        bindings("", "", "");

        BuiltinImDriver driver = driver(platform);
        driver.setup();

        assertFalse(platform.started, "凭据/启用缺失 → 不启动（Provider 层则整体 Unavailable）");
    }

    @Test
    void sendPublic_routesToBoundPlayerGroup() {
        FakePlatform platform = new FakePlatform();
        imConfig(new QqPlatformConfig(true, "app-1", "secret-1"));
        bindings("group:G-admin", "group:G-player", "user:U-1");
        BuiltinImDriver driver = driver(platform);
        driver.setup();

        driver.send(MessageEnvelope.publicMessage("公告"));

        assertEquals(List.of("qq:group:G-player|公告"), platform.sent, "PUBLIC → player_group");
    }

    @Test
    void sendPrivate_routesToAdminDm() {
        FakePlatform platform = new FakePlatform();
        imConfig(new QqPlatformConfig(true, "app-1", "secret-1"));
        bindings("group:G-admin", "", "user:U-1");
        BuiltinImDriver driver = driver(platform);
        driver.setup();

        driver.send(MessageEnvelope.privateMessage("私信"));

        assertEquals(List.of("qq:user:U-1|私信"), platform.sent);
    }

    @Test
    void send_withoutBindings_noOp() {
        FakePlatform platform = new FakePlatform();
        imConfig(new QqPlatformConfig(true, "app-1", "secret-1"));
        bindings("", "", "");

        BuiltinImDriver driver = driver(platform);
        driver.setup();
        platform.sent.clear();

        driver.send(MessageEnvelope.publicMessage("无人绑定"));
        assertTrue(platform.sent.isEmpty(), "未绑定无目标 → 静默跳过");
    }

    @Test
    void reload_credentialsChanged_restartsPlatform() {
        FakePlatform first = new FakePlatform();
        List<FakePlatform> created = new ArrayList<>();
        BuiltinImDriver driver = new BuiltinImDriver(silentLogger(), configService, formatter, cfg -> {
            FakePlatform p = new FakePlatform();
            created.add(p);
            return p;
        });
        imConfig(new QqPlatformConfig(true, "app-1", "secret-1"));
        bindings("", "", "");
        driver.setup();
        assertEquals(1, created.size());
        assertTrue(created.get(0).started);

        // 凭据变更 → 停旧建新
        imConfig(new QqPlatformConfig(true, "app-2", "secret-2"));
        driver.reloadConfig();

        assertEquals(2, created.size(), "凭据变更应重建平台适配器");
        assertTrue(created.get(0).stopped, "旧适配器应停止");
        assertTrue(created.get(1).started, "新适配器应启动");
    }

    @Test
    void tearDown_stopsPlatform() {
        FakePlatform platform = new FakePlatform();
        imConfig(new QqPlatformConfig(true, "app-1", "secret-1"));
        bindings("", "", "");

        BuiltinImDriver driver = driver(platform);
        driver.setup();
        driver.tearDown();

        assertTrue(platform.stopped);
    }

    // =====================================================================
    // F1 多平台泛化：第二平台槽（feishu）注册 / reconcile / 路由
    // =====================================================================

    /** 第二平台（飞书形态）最小配置：字段与 QQ 不同（app_secret），验证槽支持任意配置类型。 */
    private record FeishuLikeConfig(boolean enabled, String appId, String appSecret) {
        static final FeishuLikeConfig DISABLED = new FeishuLikeConfig(false, "", "");

        boolean usable() {
            return enabled && notBlank(appId) && notBlank(appSecret);
        }

        static FeishuLikeConfig from(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return DISABLED;
            }
            return new FeishuLikeConfig(
                    section.getBoolean("enabled", false),
                    section.getString("app_id", ""),
                    section.getString("app_secret", ""));
        }

        private static boolean notBlank(String s) {
            return s != null && !s.trim().isEmpty();
        }
    }

    private static FeishuLikeConfig feishuConfig(ConfigService cs) {
        return FeishuLikeConfig.from(section(cs, "platforms.feishu"));
    }

    private static org.bukkit.configuration.ConfigurationSection section(ConfigService cs, String path) {
        if (cs.getConfig("im") == null) {
            return null;
        }
        return cs.getConfig("im").getConfigurationSection(path);
    }

    @Test
    void register_secondPlatform_reconcilesBothAndRoutesToEach() {
        // 两平台配置均可用；im_bindings 各绑一类会话（qq=player_group、feishu=admin_dm）
        YamlConfiguration im = new YamlConfiguration();
        im.set("backend", "builtin");
        im.set("platforms.qq.enabled", true);
        im.set("platforms.qq.app_id", "app-1");
        im.set("platforms.qq.client_secret", "secret-1");
        im.set("platforms.feishu.enabled", true);
        im.set("platforms.feishu.app_id", "cli-feishu");
        im.set("platforms.feishu.app_secret", "secret-feishu");
        Mockito.when(configService.getConfig("im")).thenReturn(im);

        YamlConfiguration b = new YamlConfiguration();
        b.set("sessions.qq.player_group", "qq:group:G-qq");
        b.set("sessions.feishu.admin_dm", "feishu:user:U-feishu");
        Mockito.when(configService.getConfig("im_bindings")).thenReturn(b);

        FakePlatform qq = new FakePlatform("qq");
        FakePlatform feishu = new FakePlatform("feishu");
        BuiltinImDriver driver = new BuiltinImDriver(silentLogger(), configService, formatter);
        driver.register(new PlatformSlot<>(
                "qq",
                cs -> QqPlatformConfig.from(section(cs, "platforms.qq")),
                QqPlatformConfig::usable,
                c -> qq,
                silentLogger()));
        driver.register(new PlatformSlot<>(
                "feishu", cs -> feishuConfig(cs), FeishuLikeConfig::usable, c -> feishu, silentLogger()));
        driver.setup();

        assertTrue(qq.started, "QQ 平台应启动");
        assertTrue(feishu.started, "飞书平台应启动");

        // PUBLIC → 仅绑定的 qq player_group；PRIVATE → 仅 feishu admin_dm
        driver.send(MessageEnvelope.publicMessage("群公告"));
        driver.send(MessageEnvelope.privateMessage("私信"));
        assertEquals(List.of("qq:group:G-qq|群公告"), qq.sent);
        assertEquals(List.of("feishu:user:U-feishu|私信"), feishu.sent);
    }

    @Test
    void register_onePlatformUnusable_otherStillServes() {
        YamlConfiguration im = new YamlConfiguration();
        im.set("backend", "builtin");
        im.set("platforms.qq.enabled", true);
        im.set("platforms.qq.app_id", "app-1");
        im.set("platforms.qq.client_secret", "secret-1");
        im.set("platforms.feishu.enabled", true);
        im.set("platforms.feishu.app_id", ""); // 飞书凭据缺失 → 不可用
        im.set("platforms.feishu.app_secret", "");
        Mockito.when(configService.getConfig("im")).thenReturn(im);

        YamlConfiguration b = new YamlConfiguration();
        b.set("sessions.qq.player_group", "qq:group:G-qq");
        b.set("sessions.feishu.player_group", "feishu:group:G-fs");
        Mockito.when(configService.getConfig("im_bindings")).thenReturn(b);

        FakePlatform qq = new FakePlatform("qq");
        FakePlatform feishu = new FakePlatform("feishu");
        BuiltinImDriver driver = new BuiltinImDriver(silentLogger(), configService, formatter);
        driver.register(new PlatformSlot<>(
                "qq",
                cs -> QqPlatformConfig.from(section(cs, "platforms.qq")),
                QqPlatformConfig::usable,
                c -> qq,
                silentLogger()));
        driver.register(new PlatformSlot<>(
                "feishu", cs -> feishuConfig(cs), FeishuLikeConfig::usable, c -> feishu, silentLogger()));
        driver.setup();

        assertTrue(qq.started);
        assertFalse(feishu.started, "凭据缺失平台不启动（不阻塞其他平台）");
        driver.send(MessageEnvelope.publicMessage("群公告"));
        assertEquals(List.of("qq:group:G-qq|群公告"), qq.sent, "仅可用平台收公告");
        assertTrue(feishu.sent.isEmpty());
    }
}
