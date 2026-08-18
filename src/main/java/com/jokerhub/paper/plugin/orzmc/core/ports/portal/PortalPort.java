package com.jokerhub.paper.plugin.orzmc.core.ports.portal;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 传送门端口接口。
 *
 * <p>定义跨服传送门的创建、查找和移除契约。
 * 生命周期管理（setup/tearDown）不属于此端口，由实现类的 {@code ServiceModule} 负责。</p>
 */
public interface PortalPort {

    /**
     * 在玩家朝向位置创建传送门。
     *
     * @param player 操作的玩家
     * @param host   目标服务器地址
     * @param port   目标服务器端口
     * @return 传送门信息（中心位置和轴向）
     */
    PortalInfo createPortal(Player player, String host, int port);

    /**
     * 查找某个位置对应的传送门目标。
     *
     * @param from 查询位置
     * @return 目标地址 "host:port" 字符串，未找到时返回 null
     */
    String findTarget(Location from);

    /**
     * 精确查找某个方块位置是否命中传送门内部（无邻域容差）。
     *
     * <p>供 Folia PlayerMoveEvent 补偿路径使用：该路径对每个方块移动求值，
     * 带容差的 {@link #findTarget} 会把触发区膨胀为「门 + 四周 1 格」，路过玩家会被误 transfer。
     * Paper 的 PlayerPortalEvent 路径（玩家已站在门内）继续使用带容差的 {@link #findTarget}。</p>
     *
     * @param location 查询位置（按方块坐标精确匹配）
     * @return 目标地址 "host:port" 字符串，未命中时返回 null
     */
    String findTargetExact(Location location);

    /**
     * 移除所有指向指定目标的传送门。
     *
     * @param target 目标地址 "host:port"
     * @return 移除的传送门数量
     */
    int removeByTarget(String target);
}
