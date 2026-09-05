/**
 * 玩家白名单管理（独立特性；被 botcommands $wl 消费）。
 * - 关键类型：WhitelistService（defaultImpl 单例模式）、WhitelistEventService（登录白名单检查）。
 * - 依赖：infra（config whitelist 段）；注意 infra 侧 server 白名单由 assembly/FeatureModule.enableForceWhitelist 应用。
 */
package com.jokerhub.paper.plugin.orzmc.features.whitelist;
