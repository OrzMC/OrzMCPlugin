package com.jokerhub.paper.plugin.orzmc.assembly;

/**
 * 两阶段初始化接口。
 *
 * <p>实现此接口的 {@link ServiceModule} 在构造完成后，
 * 组合根调用 {@link #afterPropertiesSet()} 阶段来注入无法在构造期提供的跨模块依赖。</p>
 *
 * <p>执行顺序：构造器注入 → (所有模块构造完成) → afterPropertiesSet() → setup()</p>
 */
public interface Initializable {

    /** 在所有模块实例化完成后调用，用于注入跨模块回引用。 */
    void afterPropertiesSet();
}
