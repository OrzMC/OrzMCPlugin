package com.jokerhub.paper.plugin.orzmc.core.ports.server;

import java.util.logging.Logger;

/**
 * 服务端日志门面。
 *
 * <p>Feature 层通过此接口获取 {@link Logger} 实例，不直接依赖 Bukkit 日志实现。</p>
 */
public interface ServerLogger {

    /**
     * 获取与此服务关联的日志记录器。
     *
     * @return Logger 实例
     */
    Logger logger();
}
