package com.jokerhub.paper.plugin.orzmc.features.rank;

import java.util.UUID;

/** 玩家名解析端口：离线服 UUID → 最后已知名字。 */
@FunctionalInterface
public interface PlayerNameResolver {

    String resolve(UUID playerId);
}
