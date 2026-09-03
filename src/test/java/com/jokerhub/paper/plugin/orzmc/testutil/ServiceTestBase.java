package com.jokerhub.paper.plugin.orzmc.testutil;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.MockitoAnnotations;

/**
 * 服务测试基类。
 *
 * <p>提供自动 Mockito 初始化、通用 mock 工厂方法和统一标签。
 * 子类在 {@code @BeforeEach} 中调用 {@code super.setUpBase()} 后使用工厂方法。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * class MyServiceTest extends ServiceTestBase {
 *     private MyService service;
 *
 *     @Override
 *     public void setUpBase() {
 *         super.setUpBase();
 *         service = new MyService(mockConfigProvider(), mockNotifier());
 *     }
 *
 *     @Test
 *     void testSomething() { ... }
 * }
 * }</pre>
 */
@Tag("unit")
public abstract class ServiceTestBase {

    /** 自动初始化 {@code @Mock} 和 {@code @Spy} 注解字段。 */
    @BeforeEach
    public void setUpBase() {
        MockitoAnnotations.openMocks(this);
    }

    // ---- Mock factories ----

    /**
     * 创建一个带有合理默认行为的 {@link TypedConfigProvider} mock，
     * {@code templateOptions()} 使用坐标缩放 1.0、精度 2、"block" 单位标签的默认配置。
     */
    protected TypedConfigProvider mockConfigProvider() {
        return mockConfigProvider(defaultTemplateOptions());
    }

    /**
     * 创建带指定 {@link TemplateOptions} 的 {@link TypedConfigProvider} mock。
     */
    protected TypedConfigProvider mockConfigProvider(TemplateOptions opts) {
        TypedConfigProvider mock = mock(TypedConfigProvider.class);
        when(mock.templateOptions()).thenReturn(opts);
        return mock;
    }

    /**
     * 创建 {@link Notifier} mock。
     */
    protected Notifier mockNotifier() {
        return mock(Notifier.class);
    }

    /**
     * 创建带 Adventure 文本支持的 {@link OrzTextStyles} mock，
     * 常用方法返回空 Component 以避免 NPE。
     */
    protected OrzTextStyles mockTextStyles() {
        OrzTextStyles mock = mock(OrzTextStyles.class);
        when(mock.tntPrefix()).thenReturn(Component.empty());
        when(mock.explosionPrefix()).thenReturn(Component.empty());
        when(mock.playerName(anyString())).thenReturn(Component.empty());
        when(mock.coordComponent(anyString())).thenReturn(Component.empty());
        when(mock.error(anyString())).thenReturn(Component.empty());
        when(mock.info(anyString())).thenReturn(Component.empty());
        when(mock.success(anyString())).thenReturn(Component.empty());
        when(mock.warn(anyString())).thenReturn(Component.empty());
        return mock;
    }

    /**
     * 创建 {@link CommandSender} mock。
     */
    protected CommandSender mockCommandSender() {
        return mock(CommandSender.class);
    }

    /**
     * 创建提供 {@link Server} mock 的 {@link ServerAccess} mock。
     */
    protected ServerAccess mockServerAccess() {
        ServerAccess mock = mock(ServerAccess.class);
        when(mock.server()).thenReturn(mock(Server.class));
        return mock;
    }

    /**
     * 返回默认的 {@link TemplateOptions} 实例。
     * 与 {@link #mockConfigProvider(TemplateOptions)} 配合使用。
     */
    protected TemplateOptions defaultTemplateOptions() {
        return new TemplateOptions(Map.of(), "per_sec", "ms", Map.of(), 1.0, 2, "block");
    }

    /**
     * 返回全部默认值的 {@link Templates}（维护场景文案/进度行走 record 默认，
     * 与 templates.yml 资源一致：backup='服务器地图备份中，请稍后再试'、optimize='服务器地图优化中，请稍后再试'、
     * manual='服务器维护中，请稍后再试'，见 {@code Templates.DEFAULT_MAINTENANCE_MOTD_*}）。
     */
    protected Templates defaultTemplates() {
        return Templates.from(new YamlConfiguration());
    }

    /**
     * 构造自定义维护场景模板的 {@link Templates}：仅覆盖传入的 4 个 {@code maintenance_motd_*} 键，
     * 其余模板保持 record 默认值（默认字面收敛在 {@code Templates.DEFAULT_MAINTENANCE_MOTD_*} 常量，
     * 与 templates.yml 同步）。参数传 null 表示该键用默认值。
     *
     * @param backup       {@code maintenance_motd_backup}（BACKUP 场景文案）
     * @param optimize     {@code maintenance_motd_optimize}（OPTIMIZE 场景文案）
     * @param manual       {@code maintenance_motd_manual}（MANUAL 场景文案）
     * @param progressLine {@code maintenance_motd_progress_line}（进度行模板）
     */
    protected Templates maintenanceTemplates(String backup, String optimize, String manual, String progressLine) {
        YamlConfiguration cfg = new YamlConfiguration();
        if (backup != null) cfg.set("templates.maintenance_motd_backup", backup);
        if (optimize != null) cfg.set("templates.maintenance_motd_optimize", optimize);
        if (manual != null) cfg.set("templates.maintenance_motd_manual", manual);
        if (progressLine != null) cfg.set("templates.maintenance_motd_progress_line", progressLine);
        return Templates.from(cfg);
    }
}
