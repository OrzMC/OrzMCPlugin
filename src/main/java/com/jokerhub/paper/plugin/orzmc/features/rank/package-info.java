/**
 * 权限组 / 晋升 / 玩家名颜色（LP 软依赖）——「LP 权限治理簇」枢纽。
 * - 关键类型：RankService（currentGroup/promote/降级编排，uses prison 判定坐牢禁升 + review 通知）、
 * PermissionStore（晋升统计，实现 review.ReviewStore 持久化）、RankCommandService、PlayerRankDisplayService（颜色）、
 * GamemodeCorrectionService（权限→游戏模式矫正）、LuckPermsBootstrap/Promoter（LP 启用时）、NoopRankPromoter（降级）、
 * PlayerNameResolver、LuckPermsPromoter（486 行，簇内最重）。
 * - 簇内关系：本包被 prison/review/botcommands 依赖，也反向依赖 prison/review——同簇（见 features/prison package-info）。
 * - 设计要点：LP 缺失走 Noop 降级不崩；promotionType 模板注册 ReviewType（FeatureModule 内）。
 */
package com.jokerhub.paper.plugin.orzmc.features.rank;
