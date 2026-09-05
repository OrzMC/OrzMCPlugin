/**
 * 组合根与装配层（唯一允许把所有模块/特性接在一起的层）。
 *
 * <p>关键类型：{@code PlatformModule}（平台底座）/ {@code BotModule} / {@code PortalModule} /
 * {@code MaintenanceModule} / {@code UpdateModule} / {@code FeatureModule}（跨特性服务 DAG 装配 +
 * 事件委托，经评估为合法组合根，506 行不做机械拆分）；命令侧 = {@code FeatureCommandRegistrar}
 * 薄协调器编排各特性 {@code CommandGroup} 注册器（portal/blacklist/review/rank/prison/config/update
 * 各一文件）+ 未独立化简单命令；{@code BrigadierSupport} 为纯静态拦截链助手。</p>
 *
 * <p>六边形边界：本层依赖 features/infra/commands；features 不得反向依赖本层。
 * 改特性装配/接线 = 改 {@code FeatureModule} 构造函数对应段 + 本层注册器。</p>
 */
package com.jokerhub.paper.plugin.orzmc.assembly;
