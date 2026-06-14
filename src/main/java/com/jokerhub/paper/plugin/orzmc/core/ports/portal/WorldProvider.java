package com.jokerhub.paper.plugin.orzmc.core.ports.portal;

import org.bukkit.World;

/**
 * 世界获取抽象接口。
 *
 * <p>解耦 {@code Bukkit.getWorld()} 静态调用，便于测试替身注入。</p>
 */
public interface WorldProvider {

    /**
     * 按名称获取世界。
     *
     * @param name 世界名称
     * @return 世界实例，不存在时返回 {@code null}
     */
    World getWorld(String name);
}
