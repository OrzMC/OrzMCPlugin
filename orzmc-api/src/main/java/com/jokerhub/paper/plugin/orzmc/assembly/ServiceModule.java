package com.jokerhub.paper.plugin.orzmc.assembly;

/**
 * 领域模块生命周期接口。
 *
 * <p>每个 Module 封装一个功能域的依赖组装、初始化和销毁逻辑。
 * 组合根调用 setup/tearDown 来控制插件的生命周期。</p>
 */
public interface ServiceModule {

    /** 初始化该模块（加载配置、启动服务等）。在所有模块组装完成后调用。 */
    default void setup() {}

    /** 销毁该模块（释放资源）。在插件关闭时按逆序调用。 */
    default void tearDown() {}
}
