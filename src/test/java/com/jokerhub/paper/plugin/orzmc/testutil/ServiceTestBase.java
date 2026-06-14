package com.jokerhub.paper.plugin.orzmc.testutil;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
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
        return new TemplateOptions(
                Map.of(), "per_sec", "ms", Map.of(), Map.of(), 1.0, 2, "block", Map.of(), "zh-CN",
                Map.of(), Map.of(), Map.of());
    }
}
