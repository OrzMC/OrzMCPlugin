package com.jokerhub.paper.plugin.orzmc.features.rank;

import java.util.UUID;

/**
 * 玩家时长数据源（只读）：累计在线时长从服务器原生 stats 读取。
 *
 * <p>权限状态不再本地存储——LP track 为唯一事实源（见 {@code RankPromoter}）；
 * 审核申请记录存于 reviews 节（见 {@code ReviewStore}）。</p>
 */
public interface RankStore {

    /** 累计在线时长（分钟）——读服务器原生 stats（玩家离线也可读）。 */
    long getPlaytimeMinutes(UUID playerId);
}
