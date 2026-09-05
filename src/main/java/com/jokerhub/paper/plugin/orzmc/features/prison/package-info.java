/**
 * 坐牢治理（作弊玩家强制入 prison 组，LP 软依赖）——同属「LP 权限治理簇」。
 * - 关键类型：PrisonService（isPrisoner/关入释放编排）、PrisonCommandService、LuckPermsPrisonStore（LP 启用时）、NoopPrisonStore（降级）、PrisonLpGateway 接口。
 * - 簇内关系：<b>本簇 = rank(review(prison) 三角互引，是同一权限治理域分三包</b>——prison uses rank.PlayerNameResolver/RankService + review.ReviewNotifier；
 * rank 反向 uses prison.PrisonService。改任一簇内逻辑需连带读三包 + FeatureModule 接线段 + LP 软依赖。
 * - 软依赖范式：LP 未启用时不执行 new LuckPerms*（防 NoClassDefFoundError）。
 */
package com.jokerhub.paper.plugin.orzmc.features.prison;
